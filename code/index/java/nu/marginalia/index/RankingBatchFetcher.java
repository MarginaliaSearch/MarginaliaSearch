package nu.marginalia.index;

import io.prometheus.metrics.core.datapoints.CounterDataPoint;
import io.prometheus.metrics.core.metrics.Counter;
import nu.marginalia.ffi.IoUring;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.index.forward.spans.SpansCodec;
import nu.marginalia.index.model.FeaturesCodec;
import nu.marginalia.index.model.RankableDocument;
import nu.marginalia.index.reverse.positions.PositionCodec;
import nu.marginalia.uring.UringQueue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static nu.marginalia.index.config.ForwardIndexParameters.ENTRY_SIZE;
import static nu.marginalia.index.config.ForwardIndexParameters.FEATURES_OFFSET;
import static nu.marginalia.index.config.ForwardIndexParameters.METADATA_OFFSET;
import static nu.marginalia.index.config.ForwardIndexParameters.SPANS_OFFSET;

/** Reads the per document data the ranking stage needs, forward index entries,
 *  span data and term positions, in batched io_uring submissions.  Batching lets
 *  page cache misses overlap in the device queue instead of stalling the ranking
 *  thread once per read.
 *  <p>
 *  In mmap mode, span and position data whose pages are already resident is
 *  instead read straight out of mappings of the index files, skipping both the
 *  syscall and the page cache copy.  Residency is probed with one mincore call
 *  per batch, and batches that miss take the io_uring path, which also warms
 *  the pages for subsequent queries.
 *  <p>
 *  A fetcher owns rings on the index reader's file descriptors and may only be
 *  used by one thread at a time.  Pooled instances must be discarded when the
 *  index is swapped, which owner() helps detect.
 */
public class RankingBatchFetcher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RankingBatchFetcher.class);
    private static final AtomicBoolean warnedRegisterFailure = new AtomicBoolean();

    /** Read resident span and position data from mappings of the index files
     *  instead of io_uring batches. */
    private static final boolean useMmapFetch =
            Boolean.parseBoolean(System.getProperty("index.mmapRankingFetch", "true"));

    /** Route batches that probe as non resident through the io_uring read path.
     *  Disabling this leaves such batches to fault their pages in one at a time,
     *  which is only useful for benchmarking the fault behavior. */
    private static final boolean useColdFallback = useMmapFetch
            && !Boolean.getBoolean("index.disableMmapColdFallback");

    private static final Counter metric_probe_results = Counter.builder()
            .name("wmsa_index_fetch_probe_results")
            .help("Residency probe outcomes for the ranking fetch, per data type")
            .labelNames("type", "result")
            .register();

    private final ProbeTally entriesTally = new ProbeTally("entries");
    private final ProbeTally spansTally = new ProbeTally("spans");
    private final ProbeTally positionsTally = new ProbeTally("positions");

    /** Probe outcomes per data type, counted in plain fields as a fetcher is
     *  single threaded, and flushed to the metrics when the query hands its
     *  fetcher back, keeping the fetch path free of shared state. */
    private static class ProbeTally {
        private final CounterDataPoint residentCounter;
        private final CounterDataPoint coldCounter;

        private long resident;
        private long cold;

        private ProbeTally(String type) {
            // The label lookups involve a map access, so the children are cached
            residentCounter = metric_probe_results.labelValues(type, "resident");
            coldCounter = metric_probe_results.labelValues(type, "cold");
        }

        private void flush() {
            if (resident > 0) residentCounter.inc(resident);
            if (cold > 0) coldCounter.inc(cold);

            resident = 0;
            cold = 0;
        }
    }

    public void flushMetrics() {
        entriesTally.flush();
        spansTally.flush();
        positionsTally.flush();
    }

    /** Submission chunk cap and ring queue size.  Kept modest since each ring
     *  locks its queue memory against RLIMIT_MEMLOCK. */
    private static final int RING_SIZE = 256;

    /** Capacity of the marshalling arrays.  The positions phase may need more
     *  slots than this and is handled in waves. */
    private static final int CAP = 4096;

    private final CombinedIndexReader owner;

    /** Decoder scratch is sized in the hundreds of kilobytes, so it lives with
     *  the pooled fetcher rather than being reallocated per query */
    private final PositionsBatchDecoder positionsDecoder = new PositionsBatchDecoder();

    private final UringQueue entriesRing;
    private final UringQueue spansRing;
    private final UringQueue positionsRing;

    /** Marshalling buffers and the entry destination slab, allocated from the
     *  global arena as pooled fetchers live for the duration of the process */
    private final MemorySegment entrySlab;
    private final MemorySegment buffers;
    private final MemorySegment sizes;
    private final MemorySegment offsets;

    /** Set if the entry slab was successfully registered with its ring.
     *  Registration pins memory and may be refused under RLIMIT_MEMLOCK. */
    private final boolean fixedBuffers;

    private final MemorySegment mappedData;
    private final MemorySegment mappedSpans;
    private final MemorySegment mappedPositions;

    public RankingBatchFetcher(CombinedIndexReader owner) {
        this.owner = owner;

        if (useMmapFetch) {
            mappedData = owner.mappedForwardData();
            mappedSpans = owner.mappedForwardSpans();
            mappedPositions = owner.mappedPositions();
        }
        else {
            mappedData = null;
            mappedSpans = null;
            mappedPositions = null;
        }

        entriesRing = UringQueue.open(owner.forwardDataFd(), RING_SIZE);
        try {
            spansRing = UringQueue.open(owner.forwardSpansFd(), RING_SIZE);
        }
        catch (RuntimeException e) {
            entriesRing.close();
            throw e;
        }
        try {
            positionsRing = UringQueue.open(owner.positionsFd(), RING_SIZE);
        }
        catch (RuntimeException e) {
            entriesRing.close();
            spansRing.close();
            throw e;
        }

        Arena arena = Arena.global();
        entrySlab = arena.allocate(8L * ENTRY_SIZE * CAP, 4096);
        buffers = arena.allocate(8L * CAP, 8);
        sizes = arena.allocate(4L * CAP, 8);
        offsets = arena.allocate(8L * CAP, 8);

        boolean registered = false;
        try {
            IoUring.registerBuffer(entriesRing, entrySlab.address(), entrySlab.byteSize());
            registered = true;
        }
        catch (RuntimeException e) {
            if (!warnedRegisterFailure.getAndSet(true)) {
                logger.warn("Buffer registration failed, falling back to unregistered reads. Consider raising LimitMEMLOCK", e);
            }
        }
        fixedBuffers = registered;
    }

    public CombinedIndexReader owner() {
        return owner;
    }

    public PositionsBatchDecoder positionsDecoder() {
        return positionsDecoder;
    }

    /** Sentinel in the returned spans pointer array for documents absent from the
     *  index.  A present document's pointer may hold any value including 0. */
    public static final long NO_ENTRY = Long.MIN_VALUE;

    /** Fetch the forward index entries for the batch in one submission, populating
     *  each document's metadata, features and size fields.  Returns the encoded
     *  spans pointer per document, NO_ENTRY for documents absent from the index. */
    public long[] fetchEntries(RankableDocument[] batch) {
        if (useMmapFetch && entriesResident(batch)) {
            return fetchEntriesMapped(batch);
        }

        long[] spansEncoded = new long[batch.length];
        Arrays.fill(spansEncoded, NO_ENTRY);
        long[] docOffsets = new long[batch.length];

        int n = 0;
        for (int i = 0; i < batch.length; i++) {
            long dataOffset = owner.forwardDataOffsetForDoc(batch[i].combinedDocumentId);
            docOffsets[i] = dataOffset;
            if (dataOffset < 0) {
                continue;
            }

            buffers.setAtIndex(JAVA_LONG, n, entrySlab.address() + 8L * ENTRY_SIZE * n);
            sizes.setAtIndex(JAVA_INT, n, 8 * ENTRY_SIZE);
            offsets.setAtIndex(JAVA_LONG, n, dataOffset);
            n++;
        }

        readChunked(entriesRing, n, fixedBuffers);

        var version = owner.forwardVersion();
        n = 0;
        for (int i = 0; i < batch.length; i++) {
            if (docOffsets[i] < 0) {
                continue;
            }

            long base = 8L * ENTRY_SIZE * n++;
            long features = entrySlab.get(JAVA_LONG_UNALIGNED, base + 8L * FEATURES_OFFSET);

            batch[i].docMetadata = entrySlab.get(JAVA_LONG_UNALIGNED, base + 8L * METADATA_OFFSET);
            batch[i].htmlFeatures = FeaturesCodec.getHtmlFeatures(features);
            batch[i].docSize = FeaturesCodec.getDocumentSize(features, version);

            spansEncoded[i] = entrySlab.get(JAVA_LONG_UNALIGNED, base + 8L * SPANS_OFFSET);
        }

        return spansEncoded;
    }

    private long[] fetchEntriesMapped(RankableDocument[] batch) {
        long[] spansEncoded = new long[batch.length];
        Arrays.fill(spansEncoded, NO_ENTRY);

        var version = owner.forwardVersion();

        for (int i = 0; i < batch.length; i++) {
            long dataOffset = owner.forwardDataOffsetForDoc(batch[i].combinedDocumentId);
            if (dataOffset < 0) {
                continue;
            }

            // Unaligned layouts skip the per access alignment check, the offsets
            // are in fact always long aligned
            long features = mappedData.get(JAVA_LONG_UNALIGNED, dataOffset + 8L * FEATURES_OFFSET);

            batch[i].docMetadata = mappedData.get(JAVA_LONG_UNALIGNED, dataOffset + 8L * METADATA_OFFSET);
            batch[i].htmlFeatures = FeaturesCodec.getHtmlFeatures(features);
            batch[i].docSize = FeaturesCodec.getDocumentSize(features, version);

            spansEncoded[i] = mappedData.get(JAVA_LONG_UNALIGNED, dataOffset + 8L * SPANS_OFFSET);
        }

        return spansEncoded;
    }

    /** Fetch the span data the entries point to in one submission.  Slots are null
     *  for documents without an entry. */
    public DecodableDocumentSpans[] fetchSpans(long[] spansEncoded, SegmentAllocator allocator) {
        if (useMmapFetch && spansResident(spansEncoded)) {
            return fetchSpansMapped(spansEncoded);
        }

        DecodableDocumentSpans[] ret = new DecodableDocumentSpans[spansEncoded.length];
        MemorySegment[] segments = new MemorySegment[spansEncoded.length];

        int n = 0;
        for (int i = 0; i < spansEncoded.length; i++) {
            if (spansEncoded[i] == NO_ENTRY) {
                continue;
            }

            int size = SpansCodec.decodeSize(spansEncoded[i]);
            MemorySegment segment = allocator.allocate(size, 8);
            segments[i] = segment;

            buffers.setAtIndex(JAVA_LONG, n, segment.address());
            sizes.setAtIndex(JAVA_INT, n, size);
            offsets.setAtIndex(JAVA_LONG, n, SpansCodec.decodeStartOffset(spansEncoded[i]));
            n++;
        }

        readChunked(spansRing, n, false);

        for (int i = 0; i < spansEncoded.length; i++) {
            if (segments[i] != null) {
                ret[i] = new DecodableDocumentSpans(segments[i]);
            }
        }
        return ret;
    }

    private DecodableDocumentSpans[] fetchSpansMapped(long[] spansEncoded) {
        DecodableDocumentSpans[] ret = new DecodableDocumentSpans[spansEncoded.length];

        for (int i = 0; i < spansEncoded.length; i++) {
            if (spansEncoded[i] == NO_ENTRY) {
                continue;
            }

            long start = SpansCodec.decodeStartOffset(spansEncoded[i]);
            int size = SpansCodec.decodeSize(spansEncoded[i]);

            ret[i] = new DecodableDocumentSpans(mappedSpans.asSlice(start, size));
        }

        return ret;
    }

    /** Whether the batch's entry data can be served from the mapping, probed on
     *  the first present range.  Coldness is bursty, an index swap or eviction
     *  cools whole files, so one probe stands in for the batch. */
    private boolean entriesResident(RankableDocument[] batch) {
        if (!useColdFallback) {
            return true;
        }

        long firstOffset = -1;
        for (int i = 0; i < batch.length && firstOffset < 0; i++) {
            firstOffset = owner.forwardDataOffsetForDoc(batch[i].combinedDocumentId);
        }

        if (firstOffset < 0) {
            return true;
        }

        if (!LinuxSystemCalls.isPageResident(mappedData.address() + firstOffset)) {
            entriesTally.cold++;
            return false;
        }
        entriesTally.resident++;

        return true;
    }

    /** As entriesResident, for the batch's span data */
    private boolean spansResident(long[] spansEncoded) {
        if (!useColdFallback) {
            return true;
        }

        int first = -1;
        for (int i = 0; i < spansEncoded.length; i++) {
            if (spansEncoded[i] != NO_ENTRY) {
                first = i;
                break;
            }
        }

        if (first < 0) {
            return true;
        }

        long firstAddress = mappedSpans.address() + SpansCodec.decodeStartOffset(spansEncoded[first]);
        if (!LinuxSystemCalls.isPageResident(firstAddress)) {
            spansTally.cold++;
            return false;
        }
        spansTally.resident++;

        return true;
    }

    /** The positions file is laid out term major, so a batch's reads cluster
     *  into one file region per query term, and the regions' residency is
     *  unrelated.  Each term column is probed and routed separately. */
    private boolean termRegionResident(RankableDocument[] batch, int termIdx) {
        if (!useColdFallback) {
            return true;
        }

        int first = -1;
        for (int i = 0; i < batch.length; i++) {
            if (batch[i].positionOffsets[termIdx] != 0) {
                first = i;
                break;
            }
        }

        if (first < 0) {
            return true;
        }

        long firstAddress = mappedPositions.address()
                + PositionCodec.decodeOffset(batch[first].positionOffsets[termIdx]);
        if (!LinuxSystemCalls.isPageResident(firstAddress)) {
            positionsTally.cold++;
            return false;
        }
        positionsTally.resident++;

        return true;
    }

    /** Fetch the raw term position sequences for all documents in the batch in
     *  as few submissions as the marshalling capacity allows.  The result is
     *  indexed like the batch, each element indexed like the document's offsets,
     *  with nulls where no position data exists. */
    public MemorySegment[][] fetchPositionSegments(RankableDocument[] batch, SegmentAllocator allocator) {
        if (useMmapFetch) {
            return fetchPositionSegmentsHybrid(batch, allocator);
        }

        MemorySegment[][] segments = new MemorySegment[batch.length][];

        int n = 0;
        for (int i = 0; i < batch.length; i++) {
            long[] positionOffsets = batch[i].positionOffsets;
            segments[i] = new MemorySegment[positionOffsets.length];

            for (int j = 0; j < positionOffsets.length; j++) {
                long encodedOffset = positionOffsets[j];
                if (encodedOffset == 0) {
                    continue;
                }

                if (n == CAP) {
                    readChunked(positionsRing, n, false);
                    n = 0;
                }

                int size = PositionCodec.decodeSize(encodedOffset);
                MemorySegment segment = allocator.allocate(size, 8);
                segments[i][j] = segment;

                buffers.setAtIndex(JAVA_LONG, n, segment.address());
                sizes.setAtIndex(JAVA_INT, n, size);
                offsets.setAtIndex(JAVA_LONG, n, PositionCodec.decodeOffset(encodedOffset));
                n++;
            }
        }

        readChunked(positionsRing, n, false);

        return segments;
    }

    /** Serve position data term column by term column, mapped slices for
     *  columns whose region is resident and io_uring reads for the rest. */
    private MemorySegment[][] fetchPositionSegmentsHybrid(RankableDocument[] batch, SegmentAllocator allocator) {
        MemorySegment[][] segments = new MemorySegment[batch.length][];
        if (batch.length == 0) {
            return segments;
        }

        int termCount = batch[0].positionOffsets.length;
        boolean[] termResident = new boolean[termCount];
        for (int j = 0; j < termCount; j++) {
            termResident[j] = termRegionResident(batch, j);
        }

        int n = 0;
        for (int i = 0; i < batch.length; i++) {
            long[] positionOffsets = batch[i].positionOffsets;
            segments[i] = new MemorySegment[positionOffsets.length];

            for (int j = 0; j < positionOffsets.length; j++) {
                long encodedOffset = positionOffsets[j];
                if (encodedOffset == 0) {
                    continue;
                }

                int size = PositionCodec.decodeSize(encodedOffset);

                if (termResident[j]) {
                    segments[i][j] = mappedPositions.asSlice(
                            PositionCodec.decodeOffset(encodedOffset), size);
                }
                else {
                    if (n == CAP) {
                        readChunked(positionsRing, n, false);
                        n = 0;
                    }

                    MemorySegment segment = allocator.allocate(size, 8);
                    segments[i][j] = segment;

                    buffers.setAtIndex(JAVA_LONG, n, segment.address());
                    sizes.setAtIndex(JAVA_INT, n, size);
                    offsets.setAtIndex(JAVA_LONG, n, PositionCodec.decodeOffset(encodedOffset));
                    n++;
                }
            }
        }

        readChunked(positionsRing, n, false);

        return segments;
    }

    private void readChunked(UringQueue ring, int n, boolean fixed) {
        for (int done = 0; done < n; done += RING_SIZE) {
            int chunk = Math.min(RING_SIZE, n - done);

            long buffersAddr = buffers.address() + 8L * done;
            long sizesAddr = sizes.address() + 4L * done;
            long offsetsAddr = offsets.address() + 8L * done;

            int ret = fixed
                    ? IoUring.readFixedBatchRaw(ring, chunk, buffersAddr, sizesAddr, offsetsAddr)
                    : IoUring.readBatchRaw(ring, chunk, buffersAddr, sizesAddr, offsetsAddr);

            if (ret != chunk) {
                throw new IllegalStateException("Batch read failed: " + ret + " of " + chunk);
            }
        }
    }

    @Override
    public void close() {
        entriesRing.close();
        spansRing.close();
        positionsRing.close();
    }
}

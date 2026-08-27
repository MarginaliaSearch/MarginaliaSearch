package nu.marginalia.index;

import nu.marginalia.ffi.IoUring;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.index.forward.spans.SpansCodec;
import nu.marginalia.index.model.FeaturesCodec;
import nu.marginalia.index.model.RankableDocument;
import nu.marginalia.index.reverse.positions.PositionCodec;
import nu.marginalia.index.reverse.positions.PositionsBatchDecoder;
import nu.marginalia.uring.UringQueue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static nu.marginalia.index.config.ForwardIndexParameters.FEATURES_OFFSET;
import static nu.marginalia.index.config.ForwardIndexParameters.METADATA_OFFSET;
import static nu.marginalia.index.config.ForwardIndexParameters.SPANS_OFFSET;

/** Fetches bulk ranking data from the index, using io_uring or mmap depending on
 * a residency heuristic.
 */
public class RankingBatchFetcher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RankingBatchFetcher.class);

    private static final ConcurrentLinkedQueue<RankingBatchFetcher> pool = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean warnedCreateFailure = new AtomicBoolean();

    private static final int RING_SIZE = 256;
    private static final int ARRAY_SIZE = 4096;

    // CAVEAT:  Fragile assumption that the entry size is 4, and that the fourth entry can be skipped
    // as it contains the text blob pointer
    private static final int ENTRY_SIZE = 3;

    private final CombinedIndexReader owner;

    private final PositionsBatchDecoder positionsDecoder = new PositionsBatchDecoder();

    private final UringQueue entriesRing;
    private final UringQueue spansRing;
    private final UringQueue positionsRing;

    private final MemorySegment buffers;
    private final MemorySegment sizes;
    private final MemorySegment offsets;

    private final MemorySegment mappedData;
    private final MemorySegment mappedSpans;
    private final MemorySegment mappedPositions;

    public RankingBatchFetcher(CombinedIndexReader cir) {
        this.owner = cir;

        mappedData = cir.mappedForwardData();
        mappedSpans = cir.mappedForwardSpans();
        mappedPositions = cir.mappedPositions();

        entriesRing = UringQueue.open(cir.forwardDataFd(), RING_SIZE);
        try {
            spansRing = UringQueue.open(cir.forwardSpansFd(), RING_SIZE);
        }
        catch (RuntimeException e) {
            entriesRing.close();
            throw e;
        }
        try {
            positionsRing = UringQueue.open(cir.positionsFd(), RING_SIZE);
        }
        catch (RuntimeException e) {
            entriesRing.close();
            spansRing.close();
            throw e;
        }

        Arena arena = Arena.global();
        buffers = arena.allocate(8L * ARRAY_SIZE, 8);
        sizes = arena.allocate(4L * ARRAY_SIZE, 8);
        offsets = arena.allocate(8L * ARRAY_SIZE, 8);
    }

    public static RankingBatchFetcher claim(CombinedIndexReader index) throws IOException {

        RankingBatchFetcher pooled;
        while ((pooled = pool.poll()) != null) {
            if (pooled.owner == index) {
                return pooled;
            }
            pooled.close();
        }

        if (pool.size() >= 256) {
            logger.error("RankingBatchFetcher leak: {} items in pool", pool.size());
            throw new IOException("RankingBatchFetcher leak");
        }

        try {
            return new RankingBatchFetcher(index);
        }
        catch (RuntimeException e) {
            if (!warnedCreateFailure.getAndSet(true)) {
                logger.warn("Failed to create batch fetcher, using serial reads", e);
            }
            throw new IOException("Failed to create batch fetcher", e);
        }
    }

    /** Close and remove all fetchers associated with the index
     * */
    public static void closeForIndex(CombinedIndexReader combinedIndexReader) {
        pool.removeIf(r -> {
            if (r.owner == combinedIndexReader) {
                r.close();
                return true;
            }
            return false;
        });
    }

    /** Return the fetcher to the pool for reuse */
    public void release() {
        pool.add(this);
    }

    public PositionsBatchDecoder positionsDecoder() {
        return positionsDecoder;
    }

    public static final long NO_ENTRY = Long.MIN_VALUE;

    public long[] fetchEntries(RankableDocument[] batch) {
        long[] spansEncoded = new long[batch.length];
        Arrays.fill(spansEncoded, NO_ENTRY);

        var version = owner.forwardVersion();

        for (int i = 0; i < batch.length; i++) {
            long dataOffset = owner.forwardDataOffsetForDoc(batch[i].combinedDocumentId);
            if (dataOffset < 0) {
                continue;
            }

            long features = mappedData.get(JAVA_LONG_UNALIGNED, dataOffset + 8L * FEATURES_OFFSET);

            batch[i].docMetadata = mappedData.get(JAVA_LONG_UNALIGNED, dataOffset + 8L * METADATA_OFFSET);
            batch[i].htmlFeatures = FeaturesCodec.getHtmlFeatures(features);
            batch[i].docSize = FeaturesCodec.getDocumentSize(features, version);

            spansEncoded[i] = mappedData.get(JAVA_LONG_UNALIGNED, dataOffset + 8L * SPANS_OFFSET);
        }

        return spansEncoded;
    }

    /** Fetch the span data the entries point to in one operation.
     *
     * Slots are null for documents without an entry.
     */
    public DecodableDocumentSpans[] fetchSpans(long[] spansEncoded, SegmentAllocator allocator) {
        if (spansResident(spansEncoded)) {
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

        chunkedRead(spansRing, n);

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

    /** Test first viable address for memory residence */
    private boolean spansResident(long[] spansEncoded) {

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
            return false;
        }

        return true;
    }

    public MemorySegment[][] fetchPositionSegments(RankableDocument[] batch, SegmentAllocator allocator) {
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
                    if (n == ARRAY_SIZE) {
                        chunkedRead(positionsRing, n);
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

        chunkedRead(positionsRing, n);

        return segments;
    }

    /** Test first viable address for memory residence */
    private boolean termRegionResident(RankableDocument[] batch, int termIdx) {

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
            return false;
        }

        return true;
    }

    private void chunkedRead(UringQueue ring, int n) {
        for (int done = 0; done < n; done += RING_SIZE) {
            int chunk = Math.min(RING_SIZE, n - done);

            long buffersAddr = buffers.address() + 8L * done;
            long sizesAddr = sizes.address() + 4L * done;
            long offsetsAddr = offsets.address() + 8L * done;

            int ret = IoUring.readBatchRaw(ring, chunk, buffersAddr, sizesAddr, offsetsAddr);
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

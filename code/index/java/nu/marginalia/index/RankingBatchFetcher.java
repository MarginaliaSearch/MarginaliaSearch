package nu.marginalia.index;

import io.prometheus.metrics.core.datapoints.CounterDataPoint;
import io.prometheus.metrics.core.metrics.Counter;
import nu.marginalia.WmsaHome;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.index.forward.spans.SpansCodec;
import nu.marginalia.index.model.FeaturesCodec;
import nu.marginalia.index.model.RankableDocument;
import nu.marginalia.index.reverse.positions.PositionCodec;
import nu.marginalia.index.reverse.positions.PositionsBatchDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static nu.marginalia.index.config.ForwardIndexParameters.FEATURES_OFFSET;
import static nu.marginalia.index.config.ForwardIndexParameters.METADATA_OFFSET;
import static nu.marginalia.index.config.ForwardIndexParameters.SPANS_OFFSET;

/** Groups nearby ranking reads to amortize syscall overhead. */
public class RankingBatchFetcher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RankingBatchFetcher.class);

    private static final ConcurrentLinkedQueue<RankingBatchFetcher> pool = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean warnedCreateFailure = new AtomicBoolean();

    private static final int CLUSTER_SIZE = 16;
    private static final int MERGE_GAP = 4096;
    private static final int MAX_READ_SIZE = 64 * 1024;
    private static final int MAX_ADVICE_SIZE = 1024 * 1024;

    // Ranking needs the spans pointer but not the document text pointer that follows it.
    private static final int ENTRY_BYTES = (SPANS_OFFSET + 1) * Long.BYTES;

    private static final String nodeStr = Integer.toString(WmsaHome.getServiceNode());

    private static final Counter readRequests = Counter.builder()
            .name("index_ranking_read_requests_total")
            .labelNames("operation", "node")
            .help("Nonempty ranking read requests before merging")
            .register();
    private static final Counter preads = Counter.builder()
            .name("index_ranking_preads_total")
            .labelNames("operation", "node")
            .help("Ranking pread calls")
            .register();

    private enum ReadOperation {
        ENTRIES("entries"), SPANS("spans"), POSITIONS("positions");

        // Cache the labeled data points
        final CounterDataPoint requests;
        final CounterDataPoint reads;

        ReadOperation(String label) {
            requests = readRequests.labelValues(label, nodeStr);
            reads = preads.labelValues(label, nodeStr);
        }
    }

    private final CombinedIndexReader owner;
    private final PositionsBatchDecoder positionsDecoder = new PositionsBatchDecoder();

    // Pooling allows a fetcher to move between query threads, so its arena must be shared.
    private final Arena arena = Arena.ofShared();
    private final MemorySegment entryBuffer;

    public RankingBatchFetcher(CombinedIndexReader cir) {
        this.owner = cir;
        try {
            entryBuffer = arena.allocate(CLUSTER_SIZE * (ENTRY_BYTES + MERGE_GAP), 8);
        }
        catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
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
                logger.warn("Failed to create batch fetcher", e);
            }
            throw new IOException("Failed to create batch fetcher", e);
        }
    }

    public static void closeForIndex(CombinedIndexReader combinedIndexReader) {
        pool.removeIf(r -> {
            if (r.owner == combinedIndexReader) {
                r.close();
                return true;
            }
            return false;
        });
    }

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

        MemorySegment[] segments = new MemorySegment[batch.length];
        List<ReadRequest> requests = new ArrayList<>(batch.length);

        for (int i = 0; i < batch.length; i++) {
            long offset = owner.forwardDataOffsetForDoc(batch[i].combinedDocumentId);
            if (offset >= 0) {
                requests.add(new ReadRequest(offset, ENTRY_BYTES, segments, i));
            }
        }

        requests.sort(Comparator.comparingLong(ReadRequest::offset));

        var version = owner.forwardVersion();

        for (int from = 0; from < requests.size(); from += CLUSTER_SIZE) {
            int to = Math.min(requests.size(), from + CLUSTER_SIZE);

            readCluster(owner.forwardDataFd(), requests, from, to,
                    SegmentAllocator.slicingAllocator(entryBuffer), ReadOperation.ENTRIES);

            for (int j = from; j < to; j++) {
                int i = requests.get(j).slot;
                MemorySegment entry = segments[i];

                long features = entry.get(JAVA_LONG_UNALIGNED, 8L * FEATURES_OFFSET);

                batch[i].docMetadata = entry.get(JAVA_LONG_UNALIGNED, 8L * METADATA_OFFSET);
                batch[i].htmlFeatures = FeaturesCodec.getHtmlFeatures(features);
                batch[i].docSize = FeaturesCodec.getDocumentSize(features, version);

                spansEncoded[i] = entry.get(JAVA_LONG_UNALIGNED, 8L * SPANS_OFFSET);
            }
        }
        return spansEncoded;
    }

    /** Result slots are null where the encoded offset is NO_ENTRY. */
    public DecodableDocumentSpans[] fetchSpans(long[] spansEncoded, SegmentAllocator allocator) {
        DecodableDocumentSpans[] ret = new DecodableDocumentSpans[spansEncoded.length];

        MemorySegment[] segments = new MemorySegment[spansEncoded.length];
        List<ReadRequest> requests = new ArrayList<>(spansEncoded.length);

        for (int i = 0; i < spansEncoded.length; i++) {
            if (spansEncoded[i] != NO_ENTRY) {
                requests.add(new ReadRequest(SpansCodec.decodeStartOffset(spansEncoded[i]),
                        SpansCodec.decodeSize(spansEncoded[i]), segments, i));
            }
        }
        read(owner.forwardSpansFd(), requests, allocator, ReadOperation.SPANS);

        for (int i = 0; i < segments.length; i++) {
            if (segments[i] != null) {
                ret[i] = new DecodableDocumentSpans(segments[i]);
            }
        }

        return ret;
    }

    public MemorySegment[][] fetchPositionSegments(RankableDocument[] batch, SegmentAllocator allocator) {
        MemorySegment[][] segments = new MemorySegment[batch.length][];
        List<ReadRequest> requests = new ArrayList<>();

        for (int i = 0; i < batch.length; i++) {
            long[] positionOffsets = batch[i].positionOffsets;
            segments[i] = new MemorySegment[positionOffsets.length];

            for (int j = 0; j < positionOffsets.length; j++) {
                long encodedOffset = positionOffsets[j];
                if (encodedOffset != 0) {
                    requests.add(new ReadRequest(PositionCodec.decodeOffset(encodedOffset),
                            PositionCodec.decodeSize(encodedOffset), segments[i], j));
                }
            }
        }

        read(owner.positionsFd(), requests, allocator, ReadOperation.POSITIONS);

        return segments;
    }

    private record ReadRequest(long offset, int size, MemorySegment[] target, int slot) {
        long end() {
            return offset + size;
        }
    }

    private static void read(int fd,
                             List<ReadRequest> requests,
                             SegmentAllocator allocator,
                             ReadOperation operation)
    {
        // A zero-length fadvise covers the entire remainder of the file, we don't want that...
        requests.removeIf(request -> {
            if (request.size != 0) {
                return false;
            }

            request.target[request.slot] = allocator.allocate(0, 8);
            return true;
        });

        requests.sort(Comparator.comparingLong(ReadRequest::offset));

        for (int from = 0; from < requests.size(); from += CLUSTER_SIZE) {
            int to = Math.min(requests.size(), from + CLUSTER_SIZE);
            readCluster(fd, requests, from, to, allocator, operation);
        }
    }

    private static void readCluster(int fd,
                                    List<ReadRequest> requests,
                                    int from,
                                    int to,
                                    SegmentAllocator allocator,
                                    ReadOperation operation)
    {

        // First advise to let the kernel begin to retrieve the pages we need
        for (int i = from; i < to;) {
            long start = requests.get(i).offset;
            long end = requests.get(i).end();
            i++;

            while (i < to && canMerge(start, end, requests.get(i), MAX_ADVICE_SIZE)) {
                end = Math.max(end, requests.get(i).end());
                i++;
            }

            LinuxSystemCalls.fadviseWillneed(fd, start, end - start);
        }

        // Next execute the reads
        int reads = 0;
        for (int i = from; i < to;) {
            int first = i;
            long start = requests.get(i).offset;
            long end = requests.get(i).end();
            i++;

            while (i < to && canMerge(start, end, requests.get(i), MAX_READ_SIZE)) {
                end = Math.max(end, requests.get(i).end());
                i++;
            }

            MemorySegment buffer = allocator.allocate(end - start, 8);

            if (LinuxSystemCalls.readAt(fd, buffer, start) != buffer.byteSize()) {
                throw new IllegalStateException("pread failed");
            }
            reads++;

            for (int j = first; j < i; j++) {
                ReadRequest request = requests.get(j);
                request.target[request.slot] = buffer.asSlice(request.offset - start, request.size);
            }
        }

        operation.requests.inc(to - from);
        operation.reads.inc(reads);
    }

    private static boolean canMerge(long start, long end, ReadRequest next, int maxSize) {
        return next.offset <= end + MERGE_GAP && Math.max(end, next.end()) - start <= maxSize;
    }

    @Override
    public void close() {
        arena.close();
    }
}

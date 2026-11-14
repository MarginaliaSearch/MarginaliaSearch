package nu.marginalia.index;

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.foreign.ValueLayout.*;
import static nu.marginalia.index.config.ForwardIndexParameters.FEATURES_OFFSET;
import static nu.marginalia.index.config.ForwardIndexParameters.METADATA_OFFSET;
import static nu.marginalia.index.config.ForwardIndexParameters.SPANS_OFFSET;

/** Batches ranking reads so nonresident pages can be fetched concurrently. */
public class RankingBatchFetcher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RankingBatchFetcher.class);

    private static final ConcurrentLinkedQueue<RankingBatchFetcher> pool = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean warnedCreateFailure = new AtomicBoolean();

    private static final int CLUSTER_SIZE = 16;

    // Ranking needs the spans pointer but not the document text pointer that follows it.
    private static final int ENTRY_BYTES = (SPANS_OFFSET + 1) * Long.BYTES;

    private final CombinedIndexReader owner;
    private final PositionsBatchDecoder positionsDecoder = new PositionsBatchDecoder();

    // Pooling allows a fetcher to move between query threads, so its arena must be shared.
    private final Arena arena = Arena.ofShared();
    private final MemorySegment entryBuffer;

    private final MemorySegment destinations;
    private final MemorySegment lengths;
    private final MemorySegment offsets;

    public RankingBatchFetcher(CombinedIndexReader cir) {
        this.owner = cir;
        try {
            entryBuffer = arena.allocate(CLUSTER_SIZE * ENTRY_BYTES, 8);
            destinations = arena.allocate(ADDRESS, CLUSTER_SIZE);
            lengths = arena.allocate(JAVA_INT, CLUSTER_SIZE);
            offsets = arena.allocate(JAVA_LONG, CLUSTER_SIZE);
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

        MemorySegment[] entries = new MemorySegment[CLUSTER_SIZE];
        ReadRequest[] requests = new ReadRequest[CLUSTER_SIZE];
        var version = owner.forwardVersion();

        for (int from = 0; from < batch.length; from += CLUSTER_SIZE) {
            int to = Math.min(batch.length, from + CLUSTER_SIZE);
            int count = 0;

            for (int i = from; i < to; i++) {
                long offset = owner.forwardDataOffsetForDoc(batch[i].combinedDocumentId);
                if (offset < 0) {
                    entries[i - from] = null;
                    continue;
                }

                MemorySegment entry = entryBuffer.asSlice((long) (i - from) * ENTRY_BYTES, ENTRY_BYTES);
                entries[i - from] = entry;
                requests[count++] = new ReadRequest(offset, entry);
            }

            read(owner.forwardDataFd(), requests, count);

            for (int i = from; i < to; i++) {
                MemorySegment entry = entries[i - from];
                if (entry == null) {
                    continue;
                }

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

        ReadRequest[] requests = new ReadRequest[CLUSTER_SIZE];
        int count = 0;

        for (int i = 0; i < spansEncoded.length; i++) {
            if (spansEncoded[i] == NO_ENTRY) {
                continue;
            }

            int size = SpansCodec.decodeSize(spansEncoded[i]);
            MemorySegment buffer = allocator.allocate(size, 8);
            ret[i] = new DecodableDocumentSpans(buffer);

            if (size != 0) {
                requests[count++] = new ReadRequest(SpansCodec.decodeStartOffset(spansEncoded[i]), buffer);
                if (count == CLUSTER_SIZE) {
                    read(owner.forwardSpansFd(), requests, count);
                    count = 0;
                }
            }
        }

        read(owner.forwardSpansFd(), requests, count);
        return ret;
    }

    public MemorySegment[][] fetchPositionSegments(RankableDocument[] batch, SegmentAllocator allocator) {
        MemorySegment[][] segments = new MemorySegment[batch.length][];
        ReadRequest[] requests = new ReadRequest[CLUSTER_SIZE];
        int count = 0;

        for (int i = 0; i < batch.length; i++) {
            long[] positionOffsets = batch[i].positionOffsets;
            segments[i] = new MemorySegment[positionOffsets.length];

            for (int j = 0; j < positionOffsets.length; j++) {
                long encodedOffset = positionOffsets[j];
                if (encodedOffset == 0) {
                    continue;
                }

                int size = PositionCodec.decodeSize(encodedOffset);
                MemorySegment buffer = allocator.allocate(size, 8);
                segments[i][j] = buffer;

                if (size != 0) {
                    requests[count++] = new ReadRequest(PositionCodec.decodeOffset(encodedOffset), buffer);
                    if (count == CLUSTER_SIZE) {
                        read(owner.positionsFd(), requests, count);
                        count = 0;
                    }
                }
            }
        }

        read(owner.positionsFd(), requests, count);

        return segments;
    }

    private record ReadRequest(long offset, MemorySegment buffer) {}

    private void read(int fd, ReadRequest[] requests, int count) {
        if (count == 0) {
            return;
        }

        Arrays.sort(requests, 0, count, Comparator.comparingLong(ReadRequest::offset));

        for (int i = 0; i < count; i++) {
            ReadRequest request = requests[i];
            destinations.setAtIndex(ADDRESS, i, request.buffer);
            lengths.setAtIndex(JAVA_INT, i, (int) request.buffer.byteSize());
            offsets.setAtIndex(JAVA_LONG, i, request.offset);
        }

        try {
            LinuxSystemCalls.bufferedBulkRead(fd, count, destinations, lengths, offsets);
        }
        catch (IOException e) {
            throw new IllegalStateException("Ranking batch read failed", e);
        }
    }

    @Override
    public void close() {
        arena.close();
    }
}

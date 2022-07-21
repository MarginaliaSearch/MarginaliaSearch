package nu.marginalia.util.dict;

import io.prometheus.client.Gauge;
import nu.marginalia.util.PrimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.round;
import static nu.marginalia.util.FileSizeUtil.readableSize;

/**
 *  Spiritually influenced by GNU Trove's hash maps
 *  LGPL 2.1
 */
public class DictionaryHashMap {
    private static final Logger logger = LoggerFactory.getLogger(DictionaryHashMap.class);
    private static final Gauge probe_count_metrics
            = Gauge.build("wmsa_dictionary_hash_map_probe_count", "Probing Count")
            .register();

    private final int bufferCount;
    private final IntBuffer[] buffers;
    public static final int NO_VALUE = Integer.MIN_VALUE;

    private final DictionaryData dictionaryData;

    private final long hashTableSize;
    private final int bufferSizeBytes;
    private final int intsPerBuffer;
    private final long maxProbeLength;

    private final AtomicInteger sz = new AtomicInteger(0);

    public DictionaryHashMap(long sizeMemory) {
        final int intSize = 4;

        bufferCount = 1 + (int) ((intSize*sizeMemory) / (1<<30));
        buffers = new IntBuffer[bufferCount];

        // Actually use a prime size for Donald Knuth reasons
        hashTableSize = PrimeUtil.nextPrime(sizeMemory, -1);

        intsPerBuffer = 1 + (int)(sizeMemory/ bufferCount);
        bufferSizeBytes = intSize*intsPerBuffer;
        maxProbeLength = sizeMemory/10;

        logger.info("Allocating dictionary hash map of size {}, capacity: {}",
                readableSize((long) bufferCount * bufferSizeBytes),
                hashTableSize);

        logger.info("available-size:{} memory-size:{} buffer-count: {}, buffer-size:{} ints-per-buffer:{}  max-probe-length:{}",
                      hashTableSize,   sizeMemory,    bufferCount, bufferSizeBytes,    intsPerBuffer,      maxProbeLength);

        if (((long) bufferCount * intsPerBuffer) < sizeMemory) {
            logger.error("Buffer memory is less than requested memory: {}*{} = {} < {}; this data structure is not safe to use",
                    bufferCount,
                    bufferSizeBytes, (long) bufferCount * bufferSizeBytes,
                    sizeMemory);
            throw new Error("Irrecoverable logic error");
        }
        else {
            logger.debug("Buffer size sanity checked passed");
        }

        dictionaryData = new DictionaryData((int)Math.min(1<<27, Math.max(32L, sizeMemory/4)));

        initializeBuffers();
    }

    private void initializeBuffers() {
        for (int b = 0; b < bufferCount; b++) {
            buffers[b] = ByteBuffer.allocateDirect(bufferSizeBytes).asIntBuffer();

            for (int i = 0; i < intsPerBuffer; i++) {
                buffers[b].put(i, NO_VALUE);
            }
        }
    }

    public int size() {
        return sz.get();
    }

    private int getCell(long idx) {
        int buffer = (int)(idx / intsPerBuffer);
        int bufferIdx = (int)(idx % intsPerBuffer);
        return buffers[buffer].get(bufferIdx);
    }
    private void setCell(long idx, int val) {
        int buffer = (int)(idx / intsPerBuffer);
        int bufferIdx = (int)(idx % intsPerBuffer);

        buffers[buffer].put(bufferIdx, val);
    }

    public int put(long key) {

        long hash = key & 0x7FFF_FFFF_FFFF_FFFFL;

        long idx = hash % hashTableSize;

        if (getCell(idx) == NO_VALUE) {
            return setValue(key, idx);
        }

        return putRehash(key, idx, hash);
    }

    private int putRehash(long key, long idx, long hash) {
        final long pStride = 1 + (hash % (hashTableSize - 2));

        for (long j = 1; j < maxProbeLength; j++) {
            idx = idx - pStride;

            if (idx < 0) {
                idx += hashTableSize;
            }

            final int val = getCell(idx);

            if (val == NO_VALUE) {
                probe_count_metrics.set(j);

                return setValue(key, idx);
            }
            else if (dictionaryData.keyEquals(val, key)) {
                return val;
            }
        }

        throw new IllegalStateException("DictionaryHashMap full @ size " + size() + "/" + hashTableSize + ", " + round((100.0*size()) / hashTableSize) + "%");
    }

    private int setValue(long key, long cell) {
        sz.incrementAndGet();

        int di = dictionaryData.add(key);
        setCell(cell, di);
        return di;
    }

    public int get(long key) {
        final long hash = key & 0x7FFF_FFFF_FFFF_FFFFL;
        final long cell = hash % hashTableSize;

        if (getCell(cell) == NO_VALUE) {
            return NO_VALUE;
        }
        else {
            int val = getCell(cell);

            if (dictionaryData.keyEquals(val, key)) {
                return val;
            }
        }

        return getRehash(key, cell, hash);
    }

    private int getRehash(long key, long idx, long hash) {
        final long pStride = 1 + (hash % (hashTableSize - 2));

        for (long j = 1; j < maxProbeLength; j++) {
            idx = idx - pStride;

            if (idx < 0) {
                idx += hashTableSize;
            }

            final var val = getCell(idx);

            if (val == NO_VALUE) {
                return NO_VALUE;
            }
            else if (dictionaryData.keyEquals(val, key)) {
                return val;
            }
        }

        throw new IllegalStateException("DictionaryHashMap full @ size " + size() + "/" + hashTableSize + ", " + round((100.0*size()) / hashTableSize) + "%");
    }

}

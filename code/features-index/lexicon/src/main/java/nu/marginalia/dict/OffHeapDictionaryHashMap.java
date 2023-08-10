package nu.marginalia.dict;

import nu.marginalia.util.NextPrimeUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.round;

/**
 *  Spiritually influenced by GNU Trove's hash maps
 *  LGPL 2.1
 */
public class OffHeapDictionaryHashMap implements DictionaryMap {

    private final int bufferCount;

    private final IntBuffer[] buffers;
    private final DictionaryData dictionaryData;

    private final long hashTableSize;
    private final int bufferSizeBytes;
    private final int intsPerBuffer;
    private final long maxProbeLength;

    private final AtomicInteger sz = new AtomicInteger(0);

    public OffHeapDictionaryHashMap(long sizeMemory) {
        final int intSize = 4;

        bufferCount = 1 + (int) ((intSize*sizeMemory) / (1<<30));
        buffers = new IntBuffer[bufferCount];

        // Actually use a prime size for Donald Knuth reasons
        hashTableSize = NextPrimeUtil.nextPrime(sizeMemory, -1);

        intsPerBuffer = 1 + (int)(sizeMemory/ bufferCount);
        bufferSizeBytes = intSize*intsPerBuffer;
        maxProbeLength = sizeMemory/10;

        if (((long) bufferCount * intsPerBuffer) < sizeMemory) {
            throw new Error("Buffer memory is less than requested memory; this data structure is not safe to use");
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

    @Override
    public void clear() {
        dictionaryData.clear();
        initializeBuffers();
        sz.set(0);
    }

    @Override
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

    @Override
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

    @Override
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

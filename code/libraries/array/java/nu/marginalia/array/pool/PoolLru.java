package nu.marginalia.array.pool;

import nu.marginalia.array.page.UnsafeLongArrayBuffer;

import java.util.LinkedHashMap;
import java.util.Map;

public class PoolLru extends LinkedHashMap<Long, UnsafeLongArrayBuffer> {
    public final int maxSize;

    public PoolLru(int maxSize) {
        super(maxSize, 0.75f, true);
        this.maxSize = maxSize;
    }

    public synchronized UnsafeLongArrayBuffer get(long address) {
        return super.get(address);
    }

    public synchronized UnsafeLongArrayBuffer put(long address, UnsafeLongArrayBuffer buffer) {
        return super.put(address, buffer);
    }

    protected boolean removeEldestEntry(Map.Entry<Long, UnsafeLongArrayBuffer> eldest) {
        return size() > maxSize;
    }
}

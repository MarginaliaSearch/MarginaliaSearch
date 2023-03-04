package nu.marginalia.dict;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

class DictionaryDataBank {

    private final int start_idx;

    // Humongous long-lived arrays seem to sometimes yield considerable memory overhead and
    // can make the GC behave poorly. Using off-heap memory seems preferred when their
    // lifetime is "forever"

    private final LongBuffer keys;

    private int size;
    private final int capacity;


    public DictionaryDataBank(int start_idx, int sz) {
        this.start_idx = start_idx;
        this.capacity = sz;

        keys = ByteBuffer.allocateDirect(8 * capacity).asLongBuffer();
        size = 0;
    }

    public int getStart() {
        return start_idx;
    }

    public int getEnd() {
        return start_idx + size;
    }

    public long getKey(int idx) {
        if (idx < start_idx || idx - start_idx >= size) {
            throw new IndexOutOfBoundsException(idx);
        }
        return keys.get(idx - start_idx);
    }

    public boolean keyEquals(int idx, long other) {
        if (idx < start_idx || idx - start_idx >= size) {
            throw new IndexOutOfBoundsException(idx);
        }

        return keys.get(idx - start_idx) == other;
    }

    public int add(long newKey) {
        if (size >= capacity)
            return -1;

        keys.put(size, newKey);

        return start_idx + size++;
    }

    public int getSize() {
        return size;
    }
}

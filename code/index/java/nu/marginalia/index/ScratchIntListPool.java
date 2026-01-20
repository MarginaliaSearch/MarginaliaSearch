package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/** Resettable pool for IntArrays
 * */
public class ScratchIntListPool {
    private final IntArrayList[] pool;
    private int pos = 0;

    public ScratchIntListPool(int size) {
        this.pool = new IntArrayList[size];
    }

    public IntArrayList get(int size) {
        if (pos < pool.length) {
            if (pool[pos] == null) {
                pool[pos] = new IntArrayList(size);
            }
            else {
                pool[pos].ensureCapacity(size);
                pool[pos].clear();
            }
            return pool[pos++];
        }

        return new IntArrayList(size);
    }

    public IntArrayList get() {
        return get(8);
    }

    public void reset() {
        pos = 0;
    }
}

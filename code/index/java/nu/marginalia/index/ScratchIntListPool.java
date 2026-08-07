package nu.marginalia.index;

/** Resettable pool for IntArrays
 * */
public class ScratchIntListPool {
    private final ScratchIntList[] pool;
    private int pos = 0;

    public ScratchIntListPool(int size) {
        this.pool = new ScratchIntList[size];
    }

    public ScratchIntList get(int size) {
        if (pos < pool.length) {
            if (pool[pos] == null) {
                pool[pos] = new ScratchIntList(size);
            }
            else {
                pool[pos].ensureCapacity(size);
                pool[pos].clear();
            }
            return pool[pos++];
        }

        return new ScratchIntList(size);
    }

    public ScratchIntList get() {
        return get(8);
    }

    public void reset() {
        pos = 0;
    }
}

package nu.marginalia.dict;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public class OnHeapDictionaryMap implements DictionaryMap {
    /* Use three different hash tables to get around the limitations of Java's array sizes.
     *
     * Each map fits 0.75 * 2^30 entries (~800mn); the three maps together fit a bit over 2^31 entries.
     * We're happy with 2^31.
     *
     * We'll assign each term to one of the three maps based on their modulo of 3.  We'll pray each
     * night that Long2IntOpenHashMap hash function is good enough to cope with this.  The keys we are
     * inserting are 64 bit hashes already, so odds are the rest of the bits have very good entropy.
     */
    private static final int DEFAULT_SIZE = Integer.getInteger("lexiconSizeHint", 100_000)/3;
    private final Long2IntOpenHashMap[] entries = new Long2IntOpenHashMap[3];

    public OnHeapDictionaryMap() {
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new Long2IntOpenHashMap(DEFAULT_SIZE, 0.75f);
        }
    }

    @Override
    public void clear() {
        for (var map : entries) {
            map.clear();
        }
    }

    @Override
    public int size() {
        int totalSize = 0;
        for (var map : entries) {
            totalSize += map.size();
        }
        return totalSize;
    }

    @Override
    public int put(long key) {
        int shardIdx = (int) Long.remainderUnsigned(key, 3);
        var shard = entries[shardIdx];
        int size = size();

        if (size == Integer.MAX_VALUE)
            throw new IllegalStateException("DictionaryMap is full");

        shard.putIfAbsent(key, size);

        return get(key);
    }

    @Override
    public int get(long key) {
        int shardIdx = (int) Long.remainderUnsigned(key, 3);
        var shard = entries[shardIdx];

        return shard.getOrDefault(key, NO_VALUE);
    }
}

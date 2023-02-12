package nu.marginalia.util.dict;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public class OnHeapDictionaryMap implements DictionaryMap {
    private final Long2IntOpenHashMap entries = new Long2IntOpenHashMap(100_000, 0.75f);

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public int put(long key) {
        entries.putIfAbsent(key, entries.size());
        return get(key);
    }

    @Override
    public int get(long key) {
        return entries.getOrDefault(key, NO_VALUE);
    }
}

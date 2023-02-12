package nu.marginalia.util.dict;

public interface DictionaryMap {
    int NO_VALUE = Integer.MIN_VALUE;

    static DictionaryMap create() {
        if (Boolean.getBoolean("small-ram")) {
            return new OnHeapDictionaryMap();
        }
        else {
            return new OffHeapDictionaryHashMap(1L << 31);
        }
    }

    int size();

    int put(long key);

    int get(long key);
}

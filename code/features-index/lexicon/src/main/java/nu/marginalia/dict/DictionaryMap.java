package nu.marginalia.dict;

public interface DictionaryMap {
    int NO_VALUE = Integer.MIN_VALUE;

    static DictionaryMap create() {
        return new OnHeapDictionaryMap();
    }

    void clear();

    int size();

    int put(long key);

    int get(long key);
}

package nu.marginalia.util.dict;

public interface DictionaryMap {
    int size();

    int put(long key);

    int get(long key);
}

package nu.marginalia.dict;

/** Backing store for the KeywordLexicon, available in on and off-heap versions.
 * <p>
 * The off-heap version is necessary when loading a lexicon that is too large to fit in RAM, due
 * to Java's 2GB limit on the size of a single array.  It is slower and less optimized than the on-heap version.
 * <p>
 * The off-heap version is on the precipice of being deprecated and its use is discouraged.
 */
public interface DictionaryMap {
    int NO_VALUE = Integer.MIN_VALUE;

    static DictionaryMap create() {
        // Default to on-heap version
        // TODO: Make this configurable

        return new OnHeapDictionaryMap();
    }

    void clear();

    int size();

    int put(long key);

    int get(long key);
}

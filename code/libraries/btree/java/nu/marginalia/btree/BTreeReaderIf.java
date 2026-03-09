package nu.marginalia.btree;

/** Interface for reading from a B-tree index.
 * <p>
 * A B-tree maps sorted long keys to associated long values.
 * The entry size determines how many longs are stored per entry:
 * for entry size 1, the key is the data; for entry size 2,
 * each entry consists of [key, value].
 */
public interface BTreeReaderIf {

    /** Return the format version of this B-tree file.
     * Legacy BTrees that predate versioning return 0.
     */
    default int formatVersion() { return 0; }

    /** Look up a key in the B-tree.
     *
     * @param key the key to search for
     * @return the offset (in entry-sized units) of the matching entry,
     *         or a negative value if the key is not present
     */
    long findEntry(long key);

    /** Return the number of entries stored in this B-tree. */
    int numEntries();

    /** Return the entry size (number of longs per entry). */
    int entrySize();

    /** Read the value associated with a key.
     * For entry size 2, this returns the second long in the entry.
     * For entry size 1, this returns the key itself.
     *
     * @param key the key to look up
     * @return the associated value, or -1 if the key is not present
     */
    default long getValue(long key) {
        long idx = findEntry(key);
        if (idx < 0) return -1;
        return getEntryValue(idx);
    }

    /** Read the value at a given entry index (as returned by findEntry).
     * For entry size 2, this reads the value long following the key.
     * For entry size 1, this reads the key itself.
     */
    long getEntryValue(long entryOffset);

    /** Batch lookup: for each key in the sorted input array,
     * find the associated value at the given offset within the entry.
     * Keys not found in the tree will have zero in the result.
     *
     * @param keys sorted array of keys to look up
     * @param offset offset within the entry to read (0 for the key, 1 for the first value, etc.)
     * @return array of values corresponding to each key
     */
    long[] queryData(long[] keys, int offset);
}

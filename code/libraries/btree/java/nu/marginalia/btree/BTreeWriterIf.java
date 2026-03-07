package nu.marginalia.btree;

import java.io.IOException;

/** Interface for constructing a B-tree index. */
public interface BTreeWriterIf {

    /** Calculate the file size in bytes needed to store
     * a B-tree with the given number of entries.
     */
    long calculateSize(int numEntries);

    /** Write a B-tree with the specified number of sorted entries.
     * The callback receives a sink that must be populated with
     * exactly numEntries entries, each consisting of entrySize longs.
     * Entries must be written in ascending key order.
     *
     * @param numEntries the number of entries to write
     * @param callback receives the data sink to populate
     */
    void write(int numEntries, BTreeDataSink callback) throws IOException;

    /** Callback interface for providing sorted data to the writer. */
    @FunctionalInterface
    interface BTreeDataSink {
        /** Write the sorted key-value data.
         *
         * @param sink object that accepts key-value entries
         */
        void write(EntryWriter sink) throws IOException;
    }

    /** Accepts entries for writing into the B-tree. */
    interface EntryWriter {
        /** Write a single entry. For entry size 1, only key is used.
         * For entry size 2, both key and value are written.
         */
        void put(long key, long value);
    }
}

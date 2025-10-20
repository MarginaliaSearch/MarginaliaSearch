package nu.marginalia.index.reverse.query;

import nu.marginalia.array.page.LongQueryBuffer;

/** An EntrySource is a source of entries for a query.
 */
public interface EntrySource {
    /** Fill the buffer with entries, updating its data and length appropriately. */
    void read(LongQueryBuffer buffer);

    /** Returns true if there are more entries to read. */
    boolean hasMore();

    /** Returns the name of the index, for debugging purposes. */
    String indexName();

    public int readEntries();
}

package nu.marginalia.index.journal.writer;

import nu.marginalia.index.journal.model.IndexJournalEntry;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;

import java.io.IOException;

/** Responsible for writing to the index journal.
 * <p></p>
 * @see IndexJournalWriterSingleFileImpl
 * @see IndexJournalWriterPagingImpl
 */
public interface IndexJournalWriter extends AutoCloseable {
    /** Write an entry to the journal.
     *
     * @param header the header of the entry
     * @param entry the data of the entry
     *
     * @return the number of bytes written
     */
    int put(IndexJournalEntryHeader header, IndexJournalEntryData entry);
    default int put(IndexJournalEntry entry) {
        return put(entry.header(), entry.data());
    }

    void close() throws IOException;

}

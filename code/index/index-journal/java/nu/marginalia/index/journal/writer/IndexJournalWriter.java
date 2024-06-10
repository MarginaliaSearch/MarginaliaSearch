package nu.marginalia.index.journal.writer;

import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;

import java.io.IOException;

/** Responsible for writing to the index journal.
 * <p></p>
 * @see IndexJournalWriterSingleFileImpl
 * @see IndexJournalWriterPagingImpl
 */
public interface IndexJournalWriter extends AutoCloseable {
    void close() throws IOException;

    int put(IndexJournalEntryHeader header, IndexJournalEntryData data);
}

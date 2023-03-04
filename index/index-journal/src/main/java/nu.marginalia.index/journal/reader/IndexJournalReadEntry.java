package nu.marginalia.index.journal.reader;

import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;

import java.io.DataInputStream;
import java.io.IOException;

public class IndexJournalReadEntry {
    public final IndexJournalEntryHeader header;

    private final long[] buffer;

    public IndexJournalReadEntry(IndexJournalEntryHeader header, long[] buffer) {
        this.header = header;
        this.buffer = buffer;
    }


    public static IndexJournalReadEntry read(DataInputStream inputStream) throws IOException {

        final long sizeBlock = inputStream.readLong();
        final long docId = inputStream.readLong();
        final long meta = inputStream.readLong();

        var header = new IndexJournalEntryHeader(
                (int) (sizeBlock >>> 32L),
                docId,
                meta);

        long[] buffer = new long[header.entrySize()];

        for (int i = 0; i < header.entrySize(); i++) {
            buffer[i] = inputStream.readLong();
        }

        return new IndexJournalReadEntry(header, buffer);
    }

    public long docId() {
        return header.combinedId();
    }

    public long docMeta() {
        return header.documentMeta();
    }

    public int domainId() {
        return (int) (docId() >>> 32L);
    }

    public int urlId() {
        return (int) (docId() & 0xFFFF_FFFFL);
    }

    public IndexJournalEntryData readEntry() {
        return new IndexJournalEntryData(header.entrySize(), buffer);
    }

}

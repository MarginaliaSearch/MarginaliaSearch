package nu.marginalia.index.journal.reader;

import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

public class IndexJournalReadEntry {
    public final IndexJournalEntryHeader header;

    private final long[] buffer;

    public IndexJournalReadEntry(IndexJournalEntryHeader header, long[] buffer) {
        this.header = header;
        this.buffer = buffer;
    }


    static final byte[] bytes = new byte[8*65536];
    static final LongBuffer longBuffer = ByteBuffer.wrap(bytes).asLongBuffer();

    public static IndexJournalReadEntry read(DataInputStream inputStream) throws IOException {

        final long sizeBlock = inputStream.readLong();
        final long docId = inputStream.readLong();
        final long meta = inputStream.readLong();

        var header = new IndexJournalEntryHeader(
                (int) (sizeBlock >>> 32L),
                docId,
                meta);

        inputStream.readFully(bytes, 0, 8*header.entrySize());

        long[] out = new long[header.entrySize()];
        longBuffer.get(0, out, 0, out.length);

        return new IndexJournalReadEntry(header, out);
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

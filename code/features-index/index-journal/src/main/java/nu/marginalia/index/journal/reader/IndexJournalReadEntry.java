package nu.marginalia.index.journal.reader;

import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.model.id.UrlIdCodec;

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


    record WorkArea(byte[] bytes, LongBuffer buffer) {
        WorkArea(byte[] bytes) {
            this(bytes, ByteBuffer.wrap(bytes).asLongBuffer());
        }
        WorkArea() {
            this(new byte[8*65536]);
        }
    }

    static ThreadLocal<WorkArea> pool = ThreadLocal.withInitial(WorkArea::new);

    public static IndexJournalReadEntry read(DataInputStream inputStream) throws IOException {

        final long sizeBlock = inputStream.readLong();
        final long docId = inputStream.readLong();
        final long meta = inputStream.readLong();

        var header = new IndexJournalEntryHeader(
                (int) (sizeBlock >>> 32L),
                (int) (sizeBlock & 0xFFFF_FFFFL),
                docId,
                meta);

        var workArea = pool.get();
        inputStream.readFully(workArea.bytes, 0, 8 * header.entrySize());

        long[] out = new long[header.entrySize()];
        workArea.buffer.get(0, out, 0, out.length);

        return new IndexJournalReadEntry(header, out);

    }

    public long docId() {
        return header.combinedId();
    }

    public long docMeta() {
        return header.documentMeta();
    }

    public int domainId() {
        return UrlIdCodec.getDomainId(docId());
    }

    public IndexJournalEntryData readEntry() {
        return new IndexJournalEntryData(header.entrySize(), buffer);
    }

}

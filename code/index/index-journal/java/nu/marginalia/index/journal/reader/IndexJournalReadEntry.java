package nu.marginalia.index.journal.reader;

import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.model.IndexJournalEntryTermData;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.sequence.GammaCodedSequence;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

public class IndexJournalReadEntry implements Iterable<IndexJournalEntryTermData> {
    public final IndexJournalEntryHeader header;

    private final ByteBuffer buffer;
    private final int initialPos;

    public IndexJournalReadEntry(IndexJournalEntryHeader header, ByteBuffer buffer) {
        this.header = header;
        this.buffer = buffer;
        this.initialPos = buffer.position();
    }


    static ThreadLocal<ByteBuffer> pool = ThreadLocal.withInitial(() -> ByteBuffer.allocate(8*65536));

    public static IndexJournalReadEntry read(DataInputStream inputStream) throws IOException {

        final long sizeBlock = inputStream.readLong();
        final int entrySize = (int) (sizeBlock >>> 48L);
        final int docSize = (int) ((sizeBlock >>> 32L) & 0xFFFFL);
        final int docFeatures = (int) (sizeBlock & 0xFFFF_FFFFL);
        final long docId = inputStream.readLong();
        final long meta = inputStream.readLong();


        var header = new IndexJournalEntryHeader(
                entrySize,
                docFeatures,
                docSize,
                docId,
                meta);

        var workArea = pool.get();
        inputStream.readFully(workArea.array(), 0, header.entrySize());
        workArea.position(0);
        workArea.limit(header.entrySize());

        return new IndexJournalReadEntry(header, workArea);
    }

    public long docId() {
        return header.combinedId();
    }

    public long docMeta() {
        return header.documentMeta();
    }

    public int documentFeatures() {
        return header.documentFeatures();
    }

    public int documentSize() {
        return header.documentSize();
    }

    public int domainId() {
        return UrlIdCodec.getDomainId(docId());
    }

    public void reset() {
        buffer.position(initialPos);
    }

    public Iterator<IndexJournalEntryTermData> iterator() {
        return new TermDataIterator(buffer, initialPos);
    }

}

class TermDataIterator implements Iterator<IndexJournalEntryTermData> {
    private final ByteBuffer buffer;

    TermDataIterator(ByteBuffer buffer, int initialPos) {
        this.buffer = buffer;
        this.buffer.position(initialPos);
    }

    @Override
    public boolean hasNext() {
        return buffer.position() < buffer.limit();
    }

    @Override
    public IndexJournalEntryTermData next() {
        // read the metadata for the term
        long termId = buffer.getLong();
        long meta = buffer.getShort();

        // read the size of the sequence data
        int size = buffer.get() & 0xFF;

        // slice the buffer to get the sequence data
        var slice = buffer.slice(buffer.position(), size);
        var sequence = new GammaCodedSequence(slice);

        // advance the buffer position to the next term
        buffer.position(buffer.position() + size);

        return new IndexJournalEntryTermData(termId, meta, sequence);
    }

}
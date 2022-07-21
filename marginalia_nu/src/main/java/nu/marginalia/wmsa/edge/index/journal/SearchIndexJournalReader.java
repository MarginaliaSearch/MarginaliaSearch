package nu.marginalia.wmsa.edge.index.journal;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.util.multimap.MultimapFileLongSlice;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalFileHeader;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Iterator;

import static nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntryHeader.HEADER_SIZE_LONGS;

public class SearchIndexJournalReader implements Iterable<SearchIndexJournalReader.JournalEntry>  {
    public static final long FILE_HEADER_SIZE_LONGS = 2;
    public static final long FILE_HEADER_SIZE_BYTES = 8*FILE_HEADER_SIZE_LONGS;

    public final SearchIndexJournalFileHeader fileHeader;

    private final MultimapFileLongSlice map;
    private final long committedSize;

    public SearchIndexJournalReader(MultimapFileLong map) {
        fileHeader = new SearchIndexJournalFileHeader(map.get(0), map.get(1));
        committedSize = map.get(0) / 8 - FILE_HEADER_SIZE_LONGS;

        map.advice(NativeIO.Advice.Sequential);

        this.map = map.atOffset(FILE_HEADER_SIZE_LONGS);
    }

    @NotNull
    @Override
    public Iterator<JournalEntry> iterator() {
        return new JournalEntryIterator();
    }

    private class JournalEntryIterator implements Iterator<JournalEntry> {
        private JournalEntry entry;

        @Override
        public boolean hasNext() {
            if (entry == null) {
                return committedSize > 0;
            }

            return entry.hasNext();
        }

        @Override
        public JournalEntry next() {
            if (entry == null) {
                entry = new JournalEntry(0);
            }
            else {
                entry = entry.next();
            }
            return entry;
        }
    }

    public class JournalEntry {
        private final long offset;
        public final SearchIndexJournalEntryHeader header;

        JournalEntry(long offset) {
            final long sizeBlock = map.get(offset);
            final long docId = map.get(offset + 1);

            this.offset = offset;
            this.header = new SearchIndexJournalEntryHeader(
                    (int)(sizeBlock >>> 32L),
                    docId,
                    IndexBlock.byId((int)(sizeBlock & 0xFFFF_FFFFL)));
        }

        public boolean hasNext() {
            return nextId() < committedSize;
        }
        public long docId() {
            return header.documentId();
        }
        public int domainId() {
            return (int) (docId() >>> 32L);
        }
        public int urlId() {
            return (int)(docId() & 0xFFFF_FFFFL);
        }
        public IndexBlock block() {
            return header.block();
        }
        public int wordCount() { return header.entrySize(); }

        public SearchIndexJournalEntry readEntry() {
            long[] dest = new long[header.entrySize()];
            map.read(dest, offset + HEADER_SIZE_LONGS);
            return new SearchIndexJournalEntry(header.entrySize(), dest);
        }

        public SearchIndexJournalEntry readEntryUsingBuffer(long[] dest) {
            if (dest.length >= header.entrySize()) {
                map.read(dest, header.entrySize(), offset + HEADER_SIZE_LONGS);
                return new SearchIndexJournalEntry(header.entrySize(), dest);
            }
            else {
                return readEntry();
            }
        }

        public long nextId() {
            return offset + HEADER_SIZE_LONGS + header.entrySize();
        }
        public JournalEntry next() { return new JournalEntry(nextId()); }

        public void copyToBuffer(ByteBuffer buffer) {
            var dest = buffer.asLongBuffer();
            dest.position(buffer.position() * 8);
            dest.limit(buffer.position()*8 + header.entrySize() + HEADER_SIZE_LONGS);
            map.read(dest, offset);
            buffer.position(dest.limit()*8);
        }
    }
}

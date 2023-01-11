package nu.marginalia.wmsa.edge.index.postings.journal.reader;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.util.array.LongArray;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalFileHeader;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalStatistics;
import org.jetbrains.annotations.NotNull;
import org.roaringbitmap.longlong.Roaring64Bitmap;

import java.io.IOException;
import java.util.Iterator;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public class SearchIndexJournalReaderSingleFile implements SearchIndexJournalReader {

    public final SearchIndexJournalFileHeader fileHeader;

    private final LongArray map;
    private final long committedSize;

    final Predicate<SearchIndexJournalReadEntry> entryPredicate;
    final Predicate<SearchIndexJournalEntry.Record> recordPredicate;

    public SearchIndexJournalReaderSingleFile(LongArray map) throws IOException {
        fileHeader = new SearchIndexJournalFileHeader(map.get(0), map.get(1));
        committedSize = map.get(0) / 8 - FILE_HEADER_SIZE_LONGS;

        map.advice(NativeIO.Advice.Sequential);

        this.map = map.shifted(FILE_HEADER_SIZE_LONGS);
        this.recordPredicate = null;
        this.entryPredicate = null;
    }

    public SearchIndexJournalReaderSingleFile(LongArray map, Predicate<SearchIndexJournalReadEntry> entryPredicate, Predicate<SearchIndexJournalEntry.Record> recordPredicate) throws IOException {
        fileHeader = new SearchIndexJournalFileHeader(map.get(0), map.get(1));
        committedSize = map.get(0) / 8 - FILE_HEADER_SIZE_LONGS;

        map.advice(NativeIO.Advice.Sequential);

        this.map = map.shifted(FILE_HEADER_SIZE_LONGS);

        this.recordPredicate = recordPredicate;
        this.entryPredicate = entryPredicate;
    }

    public SearchIndexJournalFileHeader fileHeader() {
        return fileHeader;
    }

    public boolean filter(SearchIndexJournalReadEntry entry) {
        return entryPredicate == null || entryPredicate.test(entry);
    }

    public boolean filter(SearchIndexJournalReadEntry entry, SearchIndexJournalEntry.Record record) {
        return (entryPredicate == null || entryPredicate.test(entry))
            && (recordPredicate == null || recordPredicate.test(record));
    }

    @Override
    public SearchIndexJournalStatistics getStatistics() {
        int highestWord = 0;
        final long[] tmpWordsBuffer = createAdequateTempBuffer();

        // Docs cardinality is a candidate for a HyperLogLog
        Roaring64Bitmap docsBitmap = new Roaring64Bitmap();

        for (var entry : this) {
            var entryData = entry.readEntryUsingBuffer(tmpWordsBuffer);

            if (filter(entry)) {
                docsBitmap.addLong(entry.docId() & 0x0000_0000_FFFF_FFFFL);

                for (var item : entryData) {
                    if (filter(entry, item)) {
                        highestWord = Integer.max(item.wordId(), highestWord);
                    }
                }
            }
        }

        return new SearchIndexJournalStatistics(highestWord, docsBitmap.getIntCardinality());
    }

    @Override
    public void forEachWordId(IntConsumer consumer) {
        final long[] tmpWordsBuffer = createAdequateTempBuffer();
        for (var entry : this) {
            var data = entry.readEntryUsingBuffer(tmpWordsBuffer);
            for (var post : data) {
                if (filter(entry, post)) {
                    consumer.accept(post.wordId());
                }
            }
        }
    }

    @Override
    public void forEachUrlIdWordId(BiIntConsumer consumer) {
        final long[] tmpWordsBuffer = createAdequateTempBuffer();
        for (var entry : this) {
            var data = entry.readEntryUsingBuffer(tmpWordsBuffer);

            for (var post : data) {
                if (filter(entry, post)) {
                    consumer.accept(entry.urlId(), post.wordId());
                }
            }
        }
    }

    @Override
    public void forEachDocIdWordId(LongIntConsumer consumer) {
        final long[] tmpWordsBuffer = createAdequateTempBuffer();
        for (var entry : this) {
            var data = entry.readEntryUsingBuffer(tmpWordsBuffer);

            for (var post : data) {
                if (filter(entry, post)) {
                    consumer.accept(entry.docId(), post.wordId());
                }
            }
        }
    }

    @Override
    public void forEachDocIdRecord(LongObjectConsumer<SearchIndexJournalEntry.Record> consumer) {
        final long[] tmpWordsBuffer = createAdequateTempBuffer();
        for (var entry : this) {
            var data = entry.readEntryUsingBuffer(tmpWordsBuffer);

            for (var post : data) {
                if (filter(entry, post)) {
                    consumer.accept(entry.docId(), post);
                }
            }
        }
    }
    @Override
    public void forEachUrlId(IntConsumer consumer) {
        for (var entry : this) {
            if (filter(entry)) {
                consumer.accept(entry.urlId());
            }
        }
    }

    @NotNull
    @Override
    public Iterator<SearchIndexJournalReadEntry> iterator() {
        return new JournalEntryIterator();
    }

    private class JournalEntryIterator implements Iterator<SearchIndexJournalReadEntry> {
        private SearchIndexJournalReadEntry entry;

        @Override
        public boolean hasNext() {
            if (entry == null) {
                return committedSize > 0;
            }

            return entry.hasNext();
        }

        @Override
        public SearchIndexJournalReadEntry next() {
            if (entry == null) {
                entry = new SearchIndexJournalReadEntry(0, map, committedSize);
            }
            else {
                entry = entry.next();
            }
            return entry;
        }
    }

}

package nu.marginalia.wmsa.edge.index.postings.journal.reader;

import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalStatistics;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.IntConsumer;

public interface SearchIndexJournalReader extends Iterable<SearchIndexJournalReadEntry> {
    long FILE_HEADER_SIZE_LONGS = 2;
    long FILE_HEADER_SIZE_BYTES = 8 * FILE_HEADER_SIZE_LONGS;

    default long[] createAdequateTempBuffer() {
        return new long[SearchIndexJournalEntry.MAX_LENGTH * SearchIndexJournalEntry.ENTRY_SIZE];
    }

    SearchIndexJournalStatistics getStatistics();

    void forEachWordId(IntConsumer consumer);

    void forEachUrlIdWordId(BiIntConsumer consumer);

    void forEachDocIdWordId(LongIntConsumer consumer);

    void forEachDocIdRecord(LongObjectConsumer<SearchIndexJournalEntry.Record> consumer);

    void forEachUrlId(IntConsumer consumer);

    @NotNull
    @Override
    Iterator<SearchIndexJournalReadEntry> iterator();

    interface BiIntConsumer {
        void accept(int left, int right);
    }

    interface LongIntConsumer {
        void accept(long left, int right);
    }

    interface LongObjectConsumer<T> {
        void accept(long left, T right);
    }

    interface IntObjectConsumer<T> {
        void accept(int left, T right);
    }
}

package nu.marginalia.index.journal.reader;

import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalFileHeader;
import nu.marginalia.index.journal.model.IndexJournalStatistics;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Iterator;
import java.util.function.IntConsumer;

public interface IndexJournalReader extends Iterable<IndexJournalReadEntry> {
    int FILE_HEADER_SIZE_LONGS = 2;
    int FILE_HEADER_SIZE_BYTES = 8 * FILE_HEADER_SIZE_LONGS;

    IndexJournalFileHeader fileHeader();

    IndexJournalStatistics getStatistics();

    void forEachWordId(IntConsumer consumer);

    void forEachUrlIdWordId(BiIntConsumer consumer);

    void forEachDocIdWordId(LongIntConsumer consumer);

    void forEachDocIdRecord(LongObjectConsumer<IndexJournalEntryData.Record> consumer);

    void forEachUrlId(IntConsumer consumer);

    @NotNull
    @Override
    Iterator<IndexJournalReadEntry> iterator();

    void close() throws IOException;

    interface BiIntConsumer {
        void accept(int left, int right);
    }

    interface LongIntConsumer {
        void accept(long left, int right);
    }

    interface LongObjectConsumer<T> {
        void accept(long left, T right);
    }

}

package nu.marginalia.index.journal.reader;

import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.model.idx.WordFlags;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

public interface IndexJournalReader extends Iterable<IndexJournalReadEntry> {
    int FILE_HEADER_SIZE_LONGS = 2;
    int FILE_HEADER_SIZE_BYTES = 8 * FILE_HEADER_SIZE_LONGS;

    static IndexJournalReader singleFile(Path fileName) throws IOException {
        return new IndexJournalReaderSingleCompressedFile(fileName);
    }

    static IndexJournalReader paging(Path baseDir) throws IOException {
        return new IndexJournalReaderPagingImpl(baseDir);
    }

    static IndexJournalReader singleFileWithPriorityFilters(Path path) throws IOException {

        long highPriorityFlags =
                WordFlags.Title.asBit()
                        | WordFlags.Subjects.asBit()
                        | WordFlags.TfIdfHigh.asBit()
                        | WordFlags.NamesWords.asBit()
                        | WordFlags.UrlDomain.asBit()
                        | WordFlags.UrlPath.asBit()
                        | WordFlags.Site.asBit()
                        | WordFlags.SiteAdjacent.asBit();

        return new IndexJournalReaderSingleCompressedFile(path, null,
                r -> (r & highPriorityFlags) != 0);
    }

    void forEachWordId(LongConsumer consumer);

    void forEachDocIdRecord(LongObjectConsumer<IndexJournalEntryData.Record> consumer);

    void forEachDocId(LongConsumer consumer);

    @NotNull
    @Override
    Iterator<IndexJournalReadEntry> iterator();

    boolean filter(IndexJournalReadEntry entry);

    boolean filter(IndexJournalReadEntry entry, IndexJournalEntryData.Record record);

    boolean filter(IndexJournalReadEntry entry, long metadata);

    void close() throws IOException;



    interface LongObjectConsumer<T> {
        void accept(long left, T right);
    }

}

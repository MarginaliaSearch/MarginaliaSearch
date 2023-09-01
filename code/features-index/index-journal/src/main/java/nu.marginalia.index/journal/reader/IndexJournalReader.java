package nu.marginalia.index.journal.reader;

import nu.marginalia.index.journal.reader.pointer.IndexJournalPointer;
import nu.marginalia.model.idx.WordFlags;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

public interface IndexJournalReader {
    int FILE_HEADER_SIZE_LONGS = 2;
    int FILE_HEADER_SIZE_BYTES = 8 * FILE_HEADER_SIZE_LONGS;

    static IndexJournalReader singleFile(Path fileName) throws IOException {
        return new IndexJournalReaderSingleFile(fileName);
    }

    static IndexJournalReader paging(Path baseDir) throws IOException {
        return new IndexJournalReaderPagingImpl(baseDir);
    }
    static IndexJournalReader filteringSingleFile(Path path, LongPredicate wordMetaFilter) throws IOException {
        return new IndexJournalReaderSingleFile(path)
                .filtering(wordMetaFilter);
    }

    default void forEachWordId(LongConsumer consumer) {
        var ptr = this.newPointer();
        while (ptr.nextDocument()) {
            while (ptr.nextRecord()) {
                consumer.accept(ptr.wordId());
            }
        }
    }
    default void forEachDocId(LongConsumer consumer) {
        var ptr = this.newPointer();
        while (ptr.nextDocument()) {
            consumer.accept(ptr.documentId());
        }
    }

    IndexJournalPointer newPointer();


    default IndexJournalReader filtering(LongPredicate termMetaFilter) {
        return new FilteringIndexJournalReader(this, termMetaFilter);
    }

    interface LongObjectConsumer<T> {
        void accept(long left, T right);
    }

}

class FilteringIndexJournalReader implements IndexJournalReader {
    private final IndexJournalReader base;
    private final LongPredicate termMetaFilter;

    FilteringIndexJournalReader(IndexJournalReader base, LongPredicate termMetaFilter) {
        this.base = base;
        this.termMetaFilter = termMetaFilter;
    }

    @Override
    public IndexJournalPointer newPointer() {
        return base
                .newPointer()
                .filterWordMeta(termMetaFilter);
    }
}
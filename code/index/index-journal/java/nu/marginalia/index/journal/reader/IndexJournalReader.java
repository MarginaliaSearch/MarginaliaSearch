package nu.marginalia.index.journal.reader;

import nu.marginalia.index.journal.reader.pointer.IndexJournalPointer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/** Tools for reading the index journal. */
public interface IndexJournalReader {
    int FILE_HEADER_SIZE_LONGS = 2;
    int FILE_HEADER_SIZE_BYTES = 8 * FILE_HEADER_SIZE_LONGS;

    int DOCUMENT_HEADER_SIZE_BYTES = 24;
    int TERM_HEADER_SIZE_BYTES = 12;

    /** Create a reader for a single file. */
    static IndexJournalReader singleFile(Path fileName) throws IOException {
        return new IndexJournalReaderSingleFile(fileName);
    }

    /** Create a reader for a set of files. */
    static IndexJournalReader paging(Path baseDir) throws IOException {
        return new IndexJournalReaderPagingImpl(baseDir);
    }

    default void forEachWordId(LongConsumer consumer) {
        var ptr = this.newPointer();
        while (ptr.nextDocument()) {
            for (var termData : ptr) {
                consumer.accept(termData.termId());
            }
        }
    }

    default void forEachDocId(LongConsumer consumer) throws IOException {
        try (var ptr = this.newPointer()) {
            while (ptr.nextDocument()) {
                consumer.accept(ptr.documentId());
            }
        }
    }

    /** Create a new pointer to the journal.  The IndexJournalPointer is
     * a two-tiered iterator that allows both iteration over document records
     * and the terms within each document.
     */
    IndexJournalPointer newPointer();

    /** Reader that filters the entries based on the term metadata. */
    default IndexJournalReader filtering(LongPredicate termMetaFilter) {
        return new FilteringIndexJournalReader(this, termMetaFilter);
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
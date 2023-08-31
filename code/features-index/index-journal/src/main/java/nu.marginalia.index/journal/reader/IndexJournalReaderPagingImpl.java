package nu.marginalia.index.journal.reader;

import com.google.common.collect.Iterators;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalStatistics;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongConsumer;

public class IndexJournalReaderPagingImpl implements IndexJournalReader {

    private final List<IndexJournalReader> readers;

    public IndexJournalReaderPagingImpl(Path baseDir) throws IOException {
        var inputFiles = IndexJournalFileNames.findJournalFiles(baseDir);
        this.readers = new ArrayList<>(inputFiles.size());

        for (var inputFile : inputFiles) {
            readers.add(new IndexJournalReaderSingleCompressedFile(inputFile));
        }
    }

    @Override
    public void forEachWordId(LongConsumer consumer) {
        for (var reader : readers) {
            reader.forEachWordId(consumer);
        }
    }

    @Override
    public void forEachDocIdRecord(LongObjectConsumer<IndexJournalEntryData.Record> consumer) {
        for (var reader : readers) {
            reader.forEachDocIdRecord(consumer);
        }
    }

    @Override
    public void forEachDocId(LongConsumer consumer) {
        for (var reader : readers) {
            reader.forEachDocId(consumer);
        }
    }

    @Override
    public @NotNull Iterator<IndexJournalReadEntry> iterator() {
        return Iterators.concat(readers.stream().map(IndexJournalReader::iterator).iterator());
    }

    @Override
    public boolean filter(IndexJournalReadEntry entry) {
        return readers.get(0).filter(entry);
    }

    @Override
    public boolean filter(IndexJournalReadEntry entry, IndexJournalEntryData.Record record) {
        return readers.get(0).filter(entry, record);
    }

    @Override
    public boolean filter(IndexJournalReadEntry entry, long metadata) {
        return readers.get(0).filter(entry, metadata);
    }

    @Override
    public void close() throws IOException {
        for (var reader : readers) {
            reader.close();
        }
    }
}

package nu.marginalia.index.journal.reader;

import nu.marginalia.index.journal.reader.pointer.IndexJournalPointer;
import nu.marginallia.index.journal.IndexJournalFileNames;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class IndexJournalReaderPagingImpl implements IndexJournalReader {

    private final List<IndexJournalReader> readers;

    public IndexJournalReaderPagingImpl(Path baseDir) throws IOException {
        var inputFiles = IndexJournalFileNames.findJournalFiles(baseDir);
        this.readers = new ArrayList<>(inputFiles.size());

        for (var inputFile : inputFiles) {
            readers.add(new IndexJournalReaderSingleFile(inputFile));
        }
    }

    @Override
    public IndexJournalPointer newPointer() {
        return IndexJournalPointer.concatenate(
                readers.stream()
                        .map(IndexJournalReader::newPointer)
                        .toArray(IndexJournalPointer[]::new)
        );
    }
}

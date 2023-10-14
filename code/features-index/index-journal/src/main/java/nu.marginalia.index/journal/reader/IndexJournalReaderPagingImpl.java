package nu.marginalia.index.journal.reader;

import nu.marginalia.index.journal.reader.pointer.IndexJournalPointer;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class IndexJournalReaderPagingImpl implements IndexJournalReader {

    private static final Logger logger = LoggerFactory.getLogger(IndexJournalReaderPagingImpl.class);
    private final List<IndexJournalReader> readers;

    public IndexJournalReaderPagingImpl(Path baseDir) throws IOException {
        var inputFiles = IndexJournalFileNames.findJournalFiles(baseDir);
        if (inputFiles.isEmpty())
            logger.warn("Creating paging index journal file in {}, found no inputs!", baseDir);
        else
            logger.info("Creating paging index journal reader for {} inputs", inputFiles.size());

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

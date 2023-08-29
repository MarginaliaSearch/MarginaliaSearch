package nu.marginalia.index.journal.writer;

import lombok.SneakyThrows;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class IndexJournalWriterPagingImpl implements IndexJournalWriter {
    private final Path outputDir;
    private int fileNumber = 0;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private IndexJournalWriter currentWriter = null;
    private int inputsForFile = 0;

    public IndexJournalWriterPagingImpl(Path outputDir) throws IOException {
        this.outputDir = outputDir;
        switchToNextWriter();

        logger.info("Creating Journal Writer {}", outputDir);
    }

    private void switchToNextWriter() throws IOException {
        if (currentWriter != null)
            currentWriter.close();

        currentWriter = new IndexJournalWriterSingleFileImpl(IndexJournalFileNames.allocateName(outputDir, fileNumber++));
    }

    @Override
    @SneakyThrows
    public void put(IndexJournalEntryHeader header, IndexJournalEntryData entry) {
        if (++inputsForFile > 100_000) {
            inputsForFile = 0;
            switchToNextWriter();
        }
        currentWriter.put(header, entry);
    }

    public void close() throws IOException {
        currentWriter.close();
    }
}

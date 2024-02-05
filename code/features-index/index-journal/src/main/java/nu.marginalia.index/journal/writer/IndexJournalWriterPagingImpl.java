package nu.marginalia.index.journal.writer;

import lombok.SneakyThrows;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/** IndexJournalWriter implementation that creates a sequence of journal files,
 * delegating to IndexJournalWriterSingleFileImpl to write the individual files.
 *
 */
public class IndexJournalWriterPagingImpl implements IndexJournalWriter {
    private final Path outputDir;
    private int fileNumber = 0;

    /* Number of entries to write to each file before switching to the next.
     *
     * A large limit increases the memory foot print of the index, but reduces
     * the construction time.  A small number increases the memory footprint, but
     * reduces the construction time.
     *
     * The limit is set to 1,000,000, which amounts to about 1 GB on disk.
     */
    private static final int SWITCH_LIMIT = Integer.getInteger("loader.journal-page-size", 1_000_000);


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
        if (++inputsForFile > SWITCH_LIMIT) {
            inputsForFile = 0;
            switchToNextWriter();
        }
        currentWriter.put(header, entry);
    }

    public void close() throws IOException {
        currentWriter.close();
    }
}

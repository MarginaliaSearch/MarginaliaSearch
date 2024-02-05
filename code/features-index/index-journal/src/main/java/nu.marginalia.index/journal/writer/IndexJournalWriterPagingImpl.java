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

    /** The maximum size of a journal file, in uncompressed bytes.
     *  This should be safely below 2 GB, since we assume in the construction
     *  of the index that this is the case!  The smaller these files are, the
     *  slower the index construction will be, but at the same time, if 2 GB
     *  is exceeded, the index construction will *quietly* fail.
     *
     *  Flap flap, Icarus!
     */
    private static final long sizeLimitBytes = 1_000_000_000; // 1 GB


    private final Logger logger = LoggerFactory.getLogger(getClass());
    private IndexJournalWriter currentWriter = null;
    private long bytesWritten = 0;

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
    public int put(IndexJournalEntryHeader header, IndexJournalEntryData entry) {
        if (bytesWritten >= sizeLimitBytes) {
            bytesWritten = 0;
            switchToNextWriter();
        }

        int writtenNow = currentWriter.put(header, entry);
        bytesWritten += writtenNow;

        return writtenNow;
    }

    public void close() throws IOException {
        currentWriter.close();
    }
}

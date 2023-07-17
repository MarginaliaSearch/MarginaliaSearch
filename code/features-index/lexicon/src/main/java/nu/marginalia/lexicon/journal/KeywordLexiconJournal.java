package nu.marginalia.lexicon.journal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Consumer;

/** The journal for the keyword lexicon.
 *  It's used both for writing the lexicon, but also for reconstructing it for reading later.
 */
public class KeywordLexiconJournal {

    private static final boolean noCommit = Boolean.getBoolean("DictionaryJournal.noCommit");

    private final KeywordLexiconJournalCommitQueue commitQueue;
    private KeywordLexiconJournalFile journalFile;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Thread commitToDiskThread;

    private volatile boolean running = true;
    private final Path journalFilePath;

    /** Create a new journal.
     *
     *  @param file The file to use for the journal.
     *  @param mode The mode to use for the journal.  If READ_ONLY, the journal will be read-only and refuse
     *              to accept new entries.
     */
    public KeywordLexiconJournal(File file, KeywordLexiconJournalMode mode) throws IOException {
        journalFilePath = file.toPath();

        if (mode == KeywordLexiconJournalMode.READ_WRITE) {
            commitQueue = new KeywordLexiconJournalCommitQueue();
            journalFile = new KeywordLexiconJournalFile(file);

            commitToDiskThread = new Thread(this::commitToDiskRunner, "CommitToDiskThread");
            commitToDiskThread.start();

            Runtime.getRuntime().addShutdownHook(new Thread(this::commitToDisk));
        }
        else {
            journalFile = new KeywordLexiconJournalFile(file);

            commitQueue = null;
            commitToDiskThread = null;
        }
    }

    public void enqueue(byte[] word) throws InterruptedException {
        if (null == commitQueue)
            throw new UnsupportedOperationException("Lexicon journal is read-only");

        commitQueue.enqueue(word);
    }

    public KeywordLexiconJournalFingerprint journalFingerprint() throws IOException {
        var attributes = Files.readAttributes(journalFilePath, BasicFileAttributes.class);

        long cTime = attributes.creationTime().toMillis();
        long mTime = attributes.lastModifiedTime().toMillis();
        long size = attributes.size();

        return new KeywordLexiconJournalFingerprint(cTime, mTime, size);
    }

    public void commitToDiskRunner() {
        if (noCommit) return;

        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            commitToDisk();
        }
    }

    public void commitToDisk() {
        List<byte[]> entries = commitQueue.getQueuedEntries();

        journalFile.writeEntriesToJournal(entries);
    }

    public void close() throws Exception {
        logger.info("Closing Journal");
        running = false;

        if (commitToDiskThread != null) {
            commitToDiskThread.join();
            commitToDisk();
        }

        if (journalFile != null) {
            journalFile.close();
        }
    }

    public void loadFile(Consumer<byte[]> loadJournalEntry) throws IOException {
        if (journalFile != null) {
            journalFile.close();
        }

        journalFile = new KeywordLexiconJournalFile(journalFilePath.toFile());
        journalFile.loadFile(loadJournalEntry);
    }
}

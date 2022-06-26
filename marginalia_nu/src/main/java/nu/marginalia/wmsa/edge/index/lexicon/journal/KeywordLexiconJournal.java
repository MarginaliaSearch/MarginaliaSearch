package nu.marginalia.wmsa.edge.index.lexicon.journal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class KeywordLexiconJournal {

    private static final boolean noCommit = Boolean.getBoolean("DictionaryJournal.noCommit");

    private final KeywordLexiconJournalCommitQueue commitQueue;
    private final KeywordLexiconJournalFile journalFile;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Thread commitToDiskThread;

    private volatile boolean running = true;

    public KeywordLexiconJournal(File file) throws IOException {
        commitQueue = new KeywordLexiconJournalCommitQueue();
        journalFile = new KeywordLexiconJournalFile(file);

        commitToDiskThread = new Thread(this::commitToDiskRunner, "CommitToDiskThread");
        commitToDiskThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::commitToDisk));
    }

    public void enqueue(String word) throws InterruptedException {
        commitQueue.enqueue(word);
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
        List<String> entries = commitQueue.getQueuedEntries();

        journalFile.writeEntriesToJournal(entries);
    }

    public void close() throws Exception {
        logger.info("Closing Journal");
        running = false;
        commitToDiskThread.join();
        commitToDisk();

        journalFile.close();
    }

    public void loadFile(Consumer<byte[]> loadJournalEntry) throws IOException {
        journalFile.loadFile(loadJournalEntry);
    }
}

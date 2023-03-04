package nu.marginalia.lexicon.journal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class KeywordLexiconJournalCommitQueue {
    private final ArrayList<byte[]> commitQueue = new ArrayList<>(10_000);
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final long BACK_PRESSURE_LIMIT = 25_000;

    public synchronized void enqueue(byte[] word) throws InterruptedException {
        for (int queueSize = commitQueue.size();
             queueSize >= BACK_PRESSURE_LIMIT;
             queueSize = commitQueue.size())
        {
            wait();
        }

        commitQueue.add(word);
    }


    public synchronized List<byte[]> getQueuedEntries() {
        List<byte[]> data;
        if (commitQueue.isEmpty()) {
            return Collections.emptyList();
        }
        else {
            data = new ArrayList<>(commitQueue);
            commitQueue.clear();
        }

        notifyAll();

        if (data.size() > BACK_PRESSURE_LIMIT) {
            logger.warn("Lexicon Journal Backpressure: {}", data.size());
        }

        return data;
    }
}

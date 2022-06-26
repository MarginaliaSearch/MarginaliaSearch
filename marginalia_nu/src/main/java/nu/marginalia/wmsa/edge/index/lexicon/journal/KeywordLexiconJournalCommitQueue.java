package nu.marginalia.wmsa.edge.index.lexicon.journal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KeywordLexiconJournalCommitQueue {
    private final ArrayList<String> commitQueue = new ArrayList<>(10_000);
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final long BACK_PRESSURE_LIMIT = 25_000;

    public synchronized void enqueue(String word) throws InterruptedException {
        for (int queueSize = commitQueue.size();
             queueSize >= BACK_PRESSURE_LIMIT;
             queueSize = commitQueue.size())
        {
            wait();
        }

        commitQueue.add(word);
    }


    public synchronized List<String> getQueuedEntries() {
        if (commitQueue.isEmpty())
            return Collections.emptyList();
        var data = new ArrayList<>(commitQueue);
        commitQueue.clear();

        notifyAll();

        if (data.size() > BACK_PRESSURE_LIMIT) {
            logger.warn("Dictionary Journal Backpressure: {}", data.size());
        }

        return data;
    }
}

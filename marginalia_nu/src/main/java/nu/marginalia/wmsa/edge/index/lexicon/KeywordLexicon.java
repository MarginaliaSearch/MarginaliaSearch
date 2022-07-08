package nu.marginalia.wmsa.edge.index.lexicon;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.prometheus.client.Gauge;
import lombok.SneakyThrows;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.wmsa.edge.index.lexicon.journal.KeywordLexiconJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KeywordLexicon implements AutoCloseable {
    private final DictionaryHashMap reverseIndex;

    private final ReadWriteLock memoryLock = new ReentrantReadWriteLock();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final AtomicInteger instances = new AtomicInteger();
    private final HashFunction hashFunction = Hashing.murmur3_128();

    private static final Gauge request_time_metrics
            = Gauge.build("wmsa_edge_index_dictionary_size", "Dictionary Size")
            .register();
    private final KeywordLexiconJournal journal;

    @SneakyThrows
    public KeywordLexicon(KeywordLexiconJournal keywordLexiconJournal, DictionaryHashMap reverseIndexHashMap) {

        journal = keywordLexiconJournal;
        reverseIndex = reverseIndexHashMap;

        logger.info("Creating dictionary writer");

        if (!instances.compareAndSet(0, 1)) {
            logger.error("MULTIPLE WRITER INSTANCES!");
        }

        journal.loadFile(this::loadJournalEntry);

        logger.info("Done creating dictionary writer");
    }

    private void loadJournalEntry(byte[] bytes) {
        final long key = hashFunction.hashBytes(bytes).padToLong();
        reverseIndex.put(key);
    }

    @SneakyThrows
    public int getOrInsert(String macroWord) {
        final long key = hashFunction.hashBytes(macroWord.getBytes()).padToLong();

        int idx = getReadOnly(key);
        if (idx >= 0)
            return idx;

        Lock lock = memoryLock.writeLock();
        try {
            lock.lock();

            // Check again to prevent race condition
            if ((idx = reverseIndex.get(key)) >= 0)
                return idx;

            journal.enqueue(macroWord);
            idx = reverseIndex.put(key);
            request_time_metrics.set(reverseIndex.size());

            return idx;
        }
        finally {
            lock.unlock();
        }
    }

    public int getReadOnly(String word) {
        return getReadOnly(hashFunction.hashBytes(word.getBytes()).padToLong());
    }

    public int getReadOnly(long hashedKey) {
        Lock lock = memoryLock.readLock();
        try {
            lock.lock();
            return reverseIndex.get(hashedKey);
        }
        finally {
            lock.unlock();
        }
    }

    public int size() {
        Lock lock = memoryLock.readLock();
        try {
            lock.lock();
            return reverseIndex.size();
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws Exception {
        logger.warn("Closing DictionaryWriter");

        journal.close();
    }

    public void commitToDisk() {
        journal.commitToDisk();
    }
}


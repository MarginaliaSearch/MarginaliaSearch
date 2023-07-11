package nu.marginalia.lexicon;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.prometheus.client.Gauge;
import lombok.SneakyThrows;
import nu.marginalia.dict.DictionaryMap;
import nu.marginalia.lexicon.journal.KeywordLexiconJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KeywordLexicon implements AutoCloseable {
    private final DictionaryMap reverseIndex;

    private final ReadWriteLock memoryLock = new ReentrantReadWriteLock();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final AtomicInteger instances = new AtomicInteger();
    private final HashFunction hashFunction = Hashing.murmur3_128();

    private static final Gauge request_time_metrics
            = Gauge.build("wmsa_edge_index_dictionary_size", "Dictionary Size")
            .register();
    private final KeywordLexiconJournal journal;

    @SneakyThrows
    public KeywordLexicon(KeywordLexiconJournal keywordLexiconJournal) {

        journal = keywordLexiconJournal;
        reverseIndex = DictionaryMap.create();

        logger.info("Creating dictionary writer");

        if (!instances.compareAndSet(0, 1)) {
            logger.error("MULTIPLE LEXICON INSTANCES!");
        }

        journal.loadFile(bytes -> reverseIndex.put(hashFunction.hashBytes(bytes).padToLong()));

        logger.info("Done creating dictionary writer");
    }

    public void reload() throws IOException {
        logger.info("Reloading dictionary writer");
        journal.loadFile(bytes -> reverseIndex.put(hashFunction.hashBytes(bytes).padToLong()));
        logger.info("Done reloading dictionary writer");
    }

    public int getOrInsert(String macroWord) {
        return getOrInsert(macroWord.getBytes(StandardCharsets.UTF_8));
    }

    @SneakyThrows
    private int getOrInsert(byte[] bytes) {
        if (bytes.length >= Byte.MAX_VALUE) {
            logger.warn("getOrInsert({}), illegal length {}", new String(bytes), bytes.length);
            return DictionaryMap.NO_VALUE;
        }

        final long key = hashFunction.hashBytes(bytes).padToLong();

        int idx = getReadOnly(key);

        if (idx < 0) {
            idx = insertNew(key, bytes);
        }

        return idx;
    }

    private int insertNew(long key, byte[] bytes) throws InterruptedException {
        Lock lock = memoryLock.writeLock();
        int idx;
        try {
            lock.lock();

            // Check again to prevent race condition
            if ((idx = reverseIndex.get(key)) >= 0)
                return idx;

            journal.enqueue(bytes);
            idx = reverseIndex.put(key);
            request_time_metrics.set(reverseIndex.size());

            return idx;
        }
        finally {
            lock.unlock();
        }
    }

    public int getReadOnly(String word) {
        final byte[] bytes = word.getBytes(StandardCharsets.UTF_8);
        return getReadOnly(hashFunction.hashBytes(bytes).padToLong());
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
        logger.warn("Closing Lexicon");

        journal.close();
    }

    public void commitToDisk() {
        journal.commitToDisk();
    }
}


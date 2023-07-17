package nu.marginalia.lexicon;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.prometheus.client.Gauge;
import lombok.SneakyThrows;
import nu.marginalia.dict.DictionaryMap;
import nu.marginalia.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.lexicon.journal.KeywordLexiconJournalFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** The keyword lexicon is used to map keywords to unique numeric IDs.
 *  This class is used to both construct the lexicon, and to read from it.
 *  <p>
 *  Readers will want to use the KeywordLexiconReadOnlyView wrapper, as it
 *  only exposes read-only methods and hides the mutating methods.
 *  <p>
 *  Between instances, the lexicon is stored in a journal file, exactly in the
 *  order they were received by the writer.  The journal file is then replayed
 *  on startup to reconstruct the lexicon, giving each term an ID according to
 *  the order they are loaded.  It is therefore important that the journal file
 *  is not tampered with, as this will cause the lexicon to be corrupted.
 * */

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

    private volatile KeywordLexiconJournalFingerprint fingerprint = null;

    @SneakyThrows
    public KeywordLexicon(KeywordLexiconJournal keywordLexiconJournal) {

        journal = keywordLexiconJournal;
        reverseIndex = DictionaryMap.create();

        logger.info("Creating dictionary writer");

        if (!instances.compareAndSet(0, 1)) {
            logger.error("MULTIPLE LEXICON INSTANCES!");
        }

        reload();

        logger.info("Done creating dictionary writer");
    }

    public boolean needsReload() throws IOException {
        var newFingerprint = journal.journalFingerprint();
        return !newFingerprint.equals(fingerprint);
    }

    /** Reload the lexicon from the journal */
    public void reload() throws IOException {
        var lock = memoryLock.writeLock();
        lock.lock();
        try {
            reverseIndex.clear();
            journal.loadFile(bytes -> reverseIndex.put(hashFunction.hashBytes(bytes).padToLong()));
            fingerprint = journal.journalFingerprint();
        }
        finally {
            lock.unlock();
        }
    }

    /** Get method that inserts the word into the lexicon if it is not present */
    public int getOrInsert(String macroWord) {
        return getOrInsert(macroWord.getBytes(StandardCharsets.UTF_8));
    }

    /** Get method that inserts the word into the lexicon if it is not present */
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

    /** Get method that does not modify the lexicon if the word is not present */
    public int getReadOnly(String word) {
        final byte[] bytes = word.getBytes(StandardCharsets.UTF_8);
        return getReadOnly(hashFunction.hashBytes(bytes).padToLong());
    }

    /** Get method that does not modify the lexicon if the word is not present */
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


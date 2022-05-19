package nu.marginalia.wmsa.edge.index.service.dictionary;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.prometheus.client.Gauge;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.crawler.domain.language.WordPatterns;
import nu.marginalia.util.dict.DictionaryHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Singleton
public class DictionaryWriter implements AutoCloseable {
    private final ArrayList<byte[]> commitQueue = new ArrayList<>(10_000);

    private final DictionaryHashMap reverseIndex;
    private boolean prepopulate;

    private final ReadWriteLock memoryLock = new ReentrantReadWriteLock();
    private final ReadWriteLock diskLock = new ReentrantReadWriteLock();
    private final RandomAccessFile raf;

    private final Map<String, Integer> stats = new HashMap<>();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    static volatile AtomicInteger instances = new AtomicInteger();

    private final TokenCompressor readOnlyTokenCompressor = new TokenCompressor(this::getReadOnly);
    private final TokenCompressor tokenCompressor = new TokenCompressor(this::get);

    private static final Gauge request_time_metrics
            = Gauge.build("wmsa_edge_index_dictionary_size", "Dictionary Size")
            .register();

    private volatile boolean running = true;
    private final Thread commitToDiskThread;
    @SneakyThrows
    public long getPos() {
        return raf.getFilePointer();
    }
    public void printStats() {
        stats
                .entrySet()
                .stream()
                .filter(e -> e.getValue() > 10)
                .sorted(Map.Entry.comparingByValue())
                .forEach(e -> System.out.println(e.getKey() + " " + e.getValue()));
    }
    @SneakyThrows @Inject
    public DictionaryWriter(
            @Named("edge-writer-dictionary-file") File dictionaryFile,
            @Named("edge-dictionary-hash-map-size") Long hashMapSize,
            boolean prepopulate) {
        logger.info("Creating dictionary writer");
        raf = new RandomAccessFile(dictionaryFile, "rw");
        reverseIndex = new DictionaryHashMap(hashMapSize);
        this.prepopulate = prepopulate;

        Lock writeLock = diskLock.writeLock();
        try {
            writeLock.lock();
            loadFile(dictionaryFile);
        }
        finally {
            writeLock.unlock();
        }

        commitToDiskThread = new Thread(this::commitToDiskRunner, "CommitToDiskThread");
        commitToDiskThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::commitToDisk));

        if (!instances.compareAndSet(0, 1)) {
            logger.error("MULTIPLE WRITER INSTANCES!");
        }
        logger.info("Done creating dictionary writer");
    }


    public void commitToDiskRunner() {
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            commitToDisk();
        }
    }

    public void prepare() {
        if (!prepopulate)
            return;

        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("dictionary/word-frequency"),
                "Could not load word frequency table");
             var br = new BufferedReader(new InputStreamReader(resource))
        ) {
            for (;;) {
                var line = br.readLine();
                if (line == null) {
                    break;
                }
                if (WordPatterns.wordPredicateEither.test(line)) {
                    get(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    @SneakyThrows
    private void loadFile(File dictionaryFile) {
        if (!dictionaryFile.exists()) {
            logger.info("File {} does not exist, can't load", dictionaryFile);
            return;
        }

        logger.info("Reading {}", dictionaryFile);

        long pos;
        if (raf.length() < 8) {
            pos = 8;
            raf.writeLong(pos);
        }
        else {
            pos = raf.readLong();
        }

        logger.info("Length {} ({})", pos, raf.length());
        if (pos == 8) {
            logger.info("Empty DB, prepopulating");
            prepare();
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(8192);

        var channel = raf.getChannel();

        long cp = channel.position();
        int debugNext = 0;
        try {
            buffer.limit(0);
            long loaded = 0;

            while (cp < pos || buffer.hasRemaining()) {
                if (buffer.limit() - buffer.position() < 4) {
                    buffer.compact();

                    long rb = channel.read(buffer);
                    if (rb <= 0) {
                        break;
                    }
                    cp += rb;

                    buffer.flip();
                }

                int len = buffer.get();
                if (debugNext > 0) {
                    logger.warn("NextLen: {} ({})", len, (char) len);
                }
                while (buffer.limit() - buffer.position() < len) {
                    buffer.compact();
                    int rb = channel.read(buffer);
                    if (rb <= 0) break;
                    cp += rb;
                    buffer.flip();
                }

                if (buffer.limit() < len) {

                    logger.warn("Partial write at end-of-file!");

                    if (cp >= pos) {
                        logger.info("... but it's ok");
                    }
                    break;
                }

                boolean negativeLen = false;
                if (len < 0) {
                    len =  (len&0xFF);
                    negativeLen = true;

                }

                byte[] data = new byte[len];
                buffer.get(data);
                if ((++loaded % 10_000_000) == 0L) {
                    logger.info("Loaded {} million items", loaded/1_000_000);
                }

                if (debugNext > 0) {
                    logger.warn("Next word {}", new String(data));
                    if (--debugNext == 0) {
                        logger.info("  ");
                    }
                }
                if (negativeLen) {
                    logger.warn("Negative length of word {} {}@{}", len, new String(data), reverseIndex.size());
                    debugNext = 10;
                }

//                if (reverseIndex.get(data) != DictionaryHashMap.NO_VALUE) {
//                    logger.error("Duplicate insert");
//                }
                reverseIndex.put(data, reverseIndex.size());
            }
        }
        catch (Exception ex) {
            logger.error("IO Exception", ex);
        }

        raf.seek(pos);
        request_time_metrics.set(reverseIndex.size());

        logger.info("Initial loading done, dictionary size {}", reverseIndex.size());
    }

    private final ByteBuffer commitBuffer = ByteBuffer.allocateDirect(4096);
    public volatile boolean noCommit = false;
    @SneakyThrows
    public void commitToDisk() {
        if (noCommit) return;

        if (!raf.getChannel().isOpen()) {
            logger.error("commitToDisk() with closed channel! Cannot commit!");
            return;
        }

        Lock memLock = memoryLock.readLock();
        List<byte[]> data;
        try {
            memLock.lock();
            if (commitQueue.isEmpty())
                return;
            data = new ArrayList<>(commitQueue);
            commitQueue.clear();
        }
        finally {
            memLock.unlock();
        }

        var channel = raf.getChannel();
        commitBuffer.clear();

        Lock writeLock = diskLock.writeLock();
        // Only acquire memory lock if there's a risk of backpressure
        if (data.size() < 1000) {
            memLock = null;
        }

        try {
            if (memLock != null) memLock.lock();
            writeLock.lock();

            long start = System.currentTimeMillis();
            int ct = data.size();

            for (byte[] item : data) {
                commitBuffer.clear();
                commitBuffer.put((byte) item.length);
                commitBuffer.put(item);
                commitBuffer.flip();

                while (commitBuffer.position() < commitBuffer.limit())
                    channel.write(commitBuffer, channel.size());
            }

            long pos = channel.size();
            commitBuffer.clear();
            commitBuffer.putLong(pos);
            commitBuffer.flip();
            channel.write(commitBuffer, 0);

            channel.force(false);

            logger.debug("Comitted {} items in {} ms", ct, System.currentTimeMillis() - start);
        }
        catch (Exception ex) {
            logger.error("Error during dictionary commit!!!", ex);
        }
        finally {
            writeLock.unlock();
            if (memLock != null) {
                memLock.unlock();
            }
        }
    }

    public int get(String macroWord) {
        byte[] word = tokenCompressor.getWordBytes(macroWord);

        Lock lock = memoryLock.readLock();
        try {
            lock.lock();
            int idx = reverseIndex.get(word);
            if (idx >= 0) {
                return idx;
            }
        }
        finally {
            lock.unlock();
        }

        lock = memoryLock.writeLock();
        try {
            lock.lock();
            int idx = reverseIndex.get(word);
            if (idx >= 0) {
                return idx;
            }

            if (!noCommit) {
                commitQueue.add(word);
            }

            idx = reverseIndex.size();

            reverseIndex.put(word, idx);

            request_time_metrics.set(reverseIndex.size());

            return idx;
        }
        finally {

            lock.unlock();
        }
    }

    public int getReadOnly(String word) {
        var bytes = readOnlyTokenCompressor.getWordBytes(word);
        if (bytes.length == 0) {
            return DictionaryHashMap.NO_VALUE;
        }
        return reverseIndex.get(bytes);
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

        running = false;
        commitToDiskThread.join();
        commitToDisk();

        raf.close();
    }

}


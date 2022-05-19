package nu.marginalia.wmsa.edge.index.service.index;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import gnu.trove.set.hash.TIntHashSet;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.service.index.wordstable.WordsTableWriter;
import nu.marginalia.wmsa.edge.index.service.query.SearchIndexPartitioner;
import nu.marginalia.util.btree.BTreeWriter;
import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.util.RandomWriteFunnel;
import nu.marginalia.util.multimap.MultimapSorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;

public class SearchIndexConverter {
    private static final long FILE_HEADER_SIZE = 12;
    private static final int CHUNK_HEADER_SIZE = 16;

    public static final BTreeContext urlsBTreeContext = new BTreeContext(5, 1, ~0, 8);

    private final long fileLength;
    private final long urlsFileSize;
    private final FileChannel urlsTmpFileChannel;
    private final int wordCount;
    private final MultimapFileLong urlsTmpFileMap;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final IndexBlock block;
    private final int bucketId;
    @org.jetbrains.annotations.NotNull
    private final File urlsFile;
    private final SearchIndexPartitioner partitioner;
    private final TIntHashSet spamDomains;
    private final MultimapSorter urlTmpFileSorter;

    @SneakyThrows
    public static long wordCount(File inputFile) {
        try (RandomAccessFile raf = new RandomAccessFile(inputFile, "r")) {
            raf.readLong();
            return raf.readInt();
        }
    }

    @SneakyThrows
    @Inject
    public SearchIndexConverter(IndexBlock block,
                                int bucketId, @Named("tmp-file-dir") Path tmpFileDir,
                                @Named("edge-writer-page-index-file") File inputFile,
                                @Named("edge-index-write-words-file") File outputFileWords,
                                @Named("edge-index-write-urls-file") File outputFileUrls,
                                SearchIndexPartitioner partitioner,
                                EdgeDomainBlacklist blacklist)
    {
        this.block = block;
        this.bucketId = bucketId;
        urlsFile = outputFileUrls;
        this.partitioner = partitioner;
        this.spamDomains = blacklist.getSpamDomains();
        logger.info("Converting {} ({}) {}", block.id, block, inputFile);

        Files.deleteIfExists(outputFileWords.toPath());
        Files.deleteIfExists(outputFileUrls.toPath());

        final RandomAccessFile raf = new RandomAccessFile(inputFile, "r");

        this.fileLength = raf.readLong();
        this.wordCount = raf.readInt();

        var inputChannel = raf.getChannel();

        ByteBuffer buffer = ByteBuffer.allocateDirect(10_000);

        urlsFileSize = getUrlsSize(buffer, raf);

        var tmpUrlsFile = Files.createTempFile(tmpFileDir, "urls-sorted", ".dat");

        var urlsTmpFileRaf = new RandomAccessFile(tmpUrlsFile.toFile(), "rw");
        urlsTmpFileChannel = new RandomAccessFile(tmpUrlsFile.toFile(), "rw").getChannel();
        urlsTmpFileMap = new MultimapFileLong(urlsTmpFileRaf, FileChannel.MapMode.READ_WRITE, urlsFileSize, 8*1024*1024, false);
        urlTmpFileSorter = urlsTmpFileMap.createSorter(tmpFileDir, 1024*1024*256);

        logger.info("Creating word index table {} for block {} ({})", outputFileWords, block.id, block);
        long[] wordIndexTable = createWordIndexTable(outputFileWords, inputChannel);

        logger.info("Creating word urls table {} for block {} ({})", outputFileUrls, block.id, block);
        createUrlTable(tmpFileDir, buffer, raf, wordIndexTable);

        Files.delete(tmpUrlsFile);
        raf.close();

        urlsTmpFileChannel.close();
        urlsTmpFileMap.force();

    }

    private boolean isUrlAllowed(long url) {
        return !spamDomains.contains((int)(url >>> 32));
    }

    public long translateUrl(long url) {
        int domainId = partitioner.translateId(bucketId, (int) (url >>> 32));
        return ((long)domainId << 32) | (url & 0xFFFFFFFFL);
    }


    @RequiredArgsConstructor
    private class IndexReader {
        private final ByteBuffer buffer;
        private final FileChannel channel;
        public long filtered;

        public void read() throws IOException {
            var lock = partitioner.getReadLock();
            try {
                lock.lock();
                outer:
                while (channel.position() < fileLength) {
                    buffer.clear();
                    buffer.limit(CHUNK_HEADER_SIZE);
                    channel.read(buffer);
                    buffer.flip();
                    long urlId = buffer.getLong();
                    int chunkBlock = buffer.getInt();
                    int count = buffer.getInt();

                    if (count > 1000) {

                        int tries = 0;
                        logger.warn("Terminating garbage @{}b, attempting repair", channel.position());

                        for (; ; ) {
                            tries++;
                            long p = channel.position();
                            buffer.clear();
                            buffer.limit(8);
                            if (channel.read(buffer) != 8) {
                                break outer; // EOF...?
                            }

                            buffer.flip();
                            int pcb = buffer.getInt();
                            int pct = buffer.getInt();
                            if (pcb == 0 || pcb == 1 && pct >= 0 && pct <= 1000) {
                                chunkBlock = pcb;
                                count = pct;
                                break;
                            } else {
                                channel.position(p + 1);
                            }
                        }
                        logger.warn("Skipped {}b", tries);
                    }

                    buffer.clear();
                    buffer.limit(count * 4);

                    int trb = 0;
                    while (trb < count * 4) {
                        int rb = channel.read(buffer);
                        if (rb <= 0) {
                            throw new ArrayIndexOutOfBoundsException(trb + " - " + count * 4 + " " + rb);
                        }
                        trb += rb;
                    }

                    buffer.flip();

                    if (isUrlAllowed(urlId)) {
                        if (block.id == chunkBlock) {
                            eachUrl(lock, count, urlId);
                        }
                    } else {
                        filtered++;
                    }
                }
            }
            finally {
                lock.unlock();
            }
        }

        public void eachUrl(Lock lock, int count, long urlId) throws IOException {
            for (int i = 0; i < count; i++) {
                int wordId = buffer.getInt();
                if (acceptWord(lock, urlId, wordId, i, block.id)) {
                    eachWord(urlId, wordId);
                }
            }
        }
        public void eachWord(long urlId, int wordId) throws IOException {

        }
    }

    private long getUrlsSize(ByteBuffer buffer, RandomAccessFile raf) throws IOException {
        raf.seek(FILE_HEADER_SIZE);

        var channel = raf.getChannel();

        var reader = new IndexReader(buffer, channel) {
            public long size;

            @Override
            public void eachWord(long urlId, int wordId) {
                size++;
            }
        };

        reader.read();

        logger.info("Blacklist filtered {} URLs", reader.filtered);
        logger.debug("URLs Size {} Mb", channel.position()/(1024*1024));

        return reader.size;
    }

    private void createUrlTable(Path tmpFileDir, ByteBuffer buffer, RandomAccessFile raf, long[] wordIndexTable) throws IOException {
        logger.debug("Table size = {}", wordIndexTable.length);
        int[] wordIndex = new int[wordIndexTable.length];
        raf.seek(FILE_HEADER_SIZE);

        var channel = raf.getChannel();

        try (RandomWriteFunnel rwf = new RandomWriteFunnel(tmpFileDir, urlsFileSize, 10_000_000)) {
            var reader = new IndexReader(buffer, channel) {
                @Override
                public void eachWord(long urlId, int wordId) throws IOException {
                    if (wordId >= wordIndex.length)
                        return;

                    if (wordId != 0) {
                        if (!(wordIndexTable[wordId - 1] + wordIndex[wordId] <= wordIndexTable[wordId])) {
                            logger.error("Crazy state: wordId={}, index={}, lower={}, upper={}",
                                    wordId,
                                    wordIndex[wordId],
                                    wordIndexTable[wordId - 1],
                                    wordIndexTable[wordId]);
                            throw new IllegalStateException();
                        }
                    }
                    if (wordId > 0) {
                        rwf.put(wordIndexTable[wordId - 1] + wordIndex[wordId]++, translateUrl(urlId));
                    } else {
                        rwf.put(wordIndex[wordId]++, translateUrl(urlId));
                    }
                }
            };

            reader.read();

            rwf.write(urlsTmpFileChannel);
        }

        urlsTmpFileChannel.force(false);

        logger.debug("URL TMP Table: {} Mb", channel.position()/(1024*1024));

        if (wordIndexTable.length > 0) {
            logger.debug("Sorting urls table");
            sortUrls(wordIndexTable);
            urlsTmpFileMap.force();
        }
        else {
            logger.warn("urls table empty -- nothing to sort");
        }


        long idx = 0;

        var copyBuffer = ByteBuffer.allocateDirect(4096);
        try (var urlsFileMap = MultimapFileLong.forOutput(urlsFile.toPath(), 1024)) {
            var writer = new BTreeWriter(urlsFileMap, urlsBTreeContext);

            if (wordIndexTable[0] != 0) {
                int start = 0;
                int end = (int) wordIndexTable[0];

                idx += writer.write(idx, (int) wordIndexTable[0],
                        offset -> urlsFileMap.transferFromFileChannel(urlsTmpFileChannel, offset, start, end));
            }

            for (int i = 1; i < wordIndexTable.length; i++) {
                if (wordIndexTable[i] != wordIndexTable[i - 1]) {
                    long start = wordIndexTable[i-1];
                    long end = wordIndexTable[i];

                    idx += writer.write(idx, (int) (end-start),
                            offset -> urlsFileMap.transferFromFileChannel(urlsTmpFileChannel, offset, start, end));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.warn("BTrees generated");
    }

    public void transfer(ByteBuffer buffer, MultimapFileLong dest, FileChannel sourceChannel, long destOffset, long sourceStart, long sourceEnd) throws IOException {
        int tbw = 0;

        buffer.limit(Math.min(buffer.capacity(), (int)(sourceEnd - sourceStart)*8));
        while (sourceEnd - sourceStart - tbw > buffer.limit()/8) {
            int bw = 0;
            while (buffer.position() < buffer.limit()) {
                int r = sourceChannel.read(buffer, sourceStart*8 + bw);
                if (r < 0) {
                    throw new IOException("");
                }
                bw += r;
            }
            buffer.flip();
            dest.write(buffer.asLongBuffer(), destOffset + tbw);
            tbw += bw/8;
            buffer.clear();
            buffer.limit(Math.min(buffer.capacity(), (int)(sourceEnd*8 - sourceStart*8 - tbw)));
        }
        buffer.clear();
        buffer.limit((int)(sourceEnd - (sourceStart + tbw))*8);
        int bw = 0;
        while (bw < buffer.limit()) {
            bw += sourceChannel.read(buffer, sourceStart + bw);
        }
        buffer.flip();
        dest.write(buffer.asLongBuffer(), destOffset + tbw);
    }

    @SneakyThrows
    private void sortUrls(long[] wordIndices) {
        urlTmpFileSorter.sort( 0, (int) wordIndices[0]);

        for (int i = 1; i < wordIndices.length; i++) {
            urlTmpFileSorter.sort(wordIndices[i-1], (int) (wordIndices[i] - wordIndices[i-1]));
        }
    }

    private long[] createWordIndexTable(File outputFileWords, FileChannel inputChannel) throws Exception {
        inputChannel.position(FILE_HEADER_SIZE);

        logger.debug("Table size = {}", wordCount);
        WordsTableWriter wordsTableWriter = new WordsTableWriter(wordCount);
        ByteBuffer buffer = ByteBuffer.allocateDirect(8*SearchIndexWriterImpl.MAX_BLOCK_SIZE);

        logger.debug("Reading words");

        var reader = new IndexReader(buffer, inputChannel) {
            @Override
            public void eachWord(long urlId, int wordId) {
                wordsTableWriter.acceptWord(wordId);
            }
        };
        reader.read();

        logger.debug("Rearranging table");

        inputChannel.position(FILE_HEADER_SIZE);

        wordsTableWriter.write(outputFileWords);

        return wordsTableWriter.getTable();
    }

    boolean acceptWord(Lock lock, long urlId, int wordId, int wordIdx, int block) {
        int domainId = (int) (urlId >>> 32L);

        if (!partitioner.filterUnsafe(lock, domainId, bucketId)) {
            return false;
        }

        return true;
    }
}


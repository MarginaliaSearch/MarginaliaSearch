package nu.marginalia.wmsa.edge.index.conversion;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import gnu.trove.set.hash.TIntHashSet;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.index.conversion.words.WordIndexOffsetsTable;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexWriterImpl;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.conversion.words.WordsTableWriter;
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
    private final Path tmpFileDir;

    private final FileChannel urlsTmpFileChannel;
    private final int wordCount;
    private final MultimapFileLong urlsTmpFileMap;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final IndexBlock block;
    private final int bucketId;


    private final File urlsFile;
    private final SearchIndexPartitioner partitioner;
    private final TIntHashSet spamDomains;
    private final MultimapSorter urlTmpFileSorter;

    private final static int internalSortLimit =
            Boolean.getBoolean("small-ram") ? 1024*1024 : 1024*1024*256;

    @SneakyThrows
    public static long wordCount(File inputFile) {
        try (RandomAccessFile raf = new RandomAccessFile(inputFile, "r")) {
            raf.readLong();
            return raf.readInt();
        }
    }

    @Inject
    public SearchIndexConverter(IndexBlock block,
                                int bucketId, @Named("tmp-file-dir") Path tmpFileDir,
                                @Named("edge-writer-page-index-file") File inputFile,
                                @Named("edge-index-write-words-file") File outputFileWords,
                                @Named("edge-index-write-urls-file") File outputFileUrls,
                                SearchIndexPartitioner partitioner,
                                EdgeDomainBlacklist blacklist)
            throws ConversionUnnecessaryException, IOException
    {
        this.block = block;
        this.bucketId = bucketId;
        this.tmpFileDir = tmpFileDir;
        this.urlsFile = outputFileUrls;
        this.partitioner = partitioner;
        this.spamDomains = blacklist.getSpamDomains();

        logger.info("Converting {} ({}) {}", block.id, block, inputFile);

        Files.deleteIfExists(outputFileWords.toPath());
        Files.deleteIfExists(outputFileUrls.toPath());

        final RandomAccessFile raf = new RandomAccessFile(inputFile, "r");

        this.fileLength = raf.readLong();
        this.wordCount = raf.readInt();

        if (fileLength <= FILE_HEADER_SIZE) {
            throw new ConversionUnnecessaryException();
        }

        var inputChannel = raf.getChannel();

        ByteBuffer buffer = ByteBuffer.allocateDirect(10_000);

        urlsFileSize = getUrlsSize(buffer, inputChannel);

        var tmpUrlsFile = Files.createTempFile(tmpFileDir, "urls-sorted", ".dat");
        var urlsTmpFileRaf = new RandomAccessFile(tmpUrlsFile.toFile(), "rw");
        urlsTmpFileChannel = urlsTmpFileRaf.getChannel();
        urlsTmpFileMap = new MultimapFileLong(urlsTmpFileRaf, FileChannel.MapMode.READ_WRITE, urlsFileSize, 8*1024*1024, false);
        urlTmpFileSorter = urlsTmpFileMap.createSorter(tmpFileDir, internalSortLimit);

        logger.info("Creating word index table {} for block {} ({})", outputFileWords, block.id, block);
        WordIndexOffsetsTable wordIndexTable = createWordIndexTable(outputFileWords, inputChannel);

        logger.info("Creating word urls table {} for block {} ({})", outputFileUrls, block.id, block);
        createUrlTable(buffer, raf, wordIndexTable);

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


    private long getUrlsSize(ByteBuffer buffer, FileChannel channel) throws IOException {
        channel.position(FILE_HEADER_SIZE);

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

    private void createUrlTable(ByteBuffer buffer, RandomAccessFile raf, WordIndexOffsetsTable wordOffsetsTable) throws IOException {
        logger.info("Table size = {}", wordOffsetsTable.length());

        raf.seek(FILE_HEADER_SIZE);

        var channel = raf.getChannel();

        try (RandomWriteFunnel rwf = new RandomWriteFunnel(tmpFileDir, urlsFileSize, 10_000_000)) {
            int[] wordWriteOffset = new int[wordOffsetsTable.length()];

            new IndexReader(buffer, channel) {
                @Override
                public void eachWord(long urlId, int wordId) throws IOException {
                    if (wordId >= wordWriteOffset.length)
                        return;

                    if (wordId > 0) {
                        rwf.put(wordOffsetsTable.get(wordId - 1) + wordWriteOffset[wordId]++, translateUrl(urlId));
                    } else {
                        rwf.put(wordWriteOffset[wordId]++, translateUrl(urlId));
                    }
                }
            }.read();

            rwf.write(urlsTmpFileChannel);
        }

        urlsTmpFileChannel.force(false);
        logger.info("URL TMP Table: {} Mb", channel.position()/(1024*1024));

        if (wordOffsetsTable.length() > 0) {
            logger.info("Sorting urls table");

            wordOffsetsTable.forEach(urlTmpFileSorter::sort);

            urlsTmpFileMap.force();
        }
        else {
            logger.warn("urls table empty -- nothing to sort");
        }

        logger.info("Writing BTree");
        try (var urlsFileMap = MultimapFileLong.forOutput(urlsFile.toPath(), 1024)) {
            var writer = new BTreeWriter(urlsFileMap, urlsBTreeContext);

            wordOffsetsTable.fold((accumulatorIdx, start, length) -> {
                // Note: The return value is accumulated into accumulatorIdx!

                return writer.write(accumulatorIdx, length,
                        slice -> slice.transferFromFileChannel(urlsTmpFileChannel, 0, start, start + length));
            });

        } catch (Exception e) {
            logger.error("Error while writing BTree", e);
        }
    }

    private WordIndexOffsetsTable createWordIndexTable(File outputFileWords, FileChannel inputChannel) throws IOException {
        inputChannel.position(FILE_HEADER_SIZE);

        logger.debug("Table size = {}", wordCount);
        WordsTableWriter wordsTableWriter = new WordsTableWriter(wordCount);
        ByteBuffer buffer = ByteBuffer.allocateDirect(8* SearchIndexWriterImpl.MAX_BLOCK_SIZE);

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
                if (acceptWord(lock, urlId)) {
                    eachWord(urlId, wordId);
                }
            }
        }
        public void eachWord(long urlId, int wordId) throws IOException {

        }

        boolean acceptWord(Lock lock, long urlId) {
            int domainId = (int) (urlId >>> 32L);

            if (!partitioner.filterUnsafe(lock, domainId, bucketId)) {
                return false;
            }

            return true;
        }
    }
}


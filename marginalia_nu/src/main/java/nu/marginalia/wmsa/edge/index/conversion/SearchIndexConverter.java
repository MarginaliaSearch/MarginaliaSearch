package nu.marginalia.wmsa.edge.index.conversion;

import nu.marginalia.util.RandomWriteFunnel;
import nu.marginalia.util.btree.BTreeWriter;
import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.index.conversion.words.WordIndexOffsetsTable;
import nu.marginalia.wmsa.edge.index.conversion.words.WordsTableWriter;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalReader;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntry.MAX_LENGTH;

public class SearchIndexConverter {
    public static final BTreeContext urlsBTreeContext = new BTreeContext(5, 1, ~0, 8);

    private final long[] tmpWordsBuffer = new long[MAX_LENGTH];

    private final Path tmpFileDir;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final IndexBlock block;
    private final int bucketId;

    private final File inputFile;
    private final File outputFileWords;
    private final File outputFileUrls;

    private final SearchIndexPartitioner partitioner;
    private final EdgeDomainBlacklist blacklist;

    private final static int internalSortLimit =
            Boolean.getBoolean("small-ram") ? 1024*1024 : 1024*1024*256;

    public SearchIndexConverter(IndexBlock block,
                                int bucketId,
                                Path tmpFileDir,
                                File inputFile,
                                File outputFileWords,
                                File outputFileUrls,
                                SearchIndexPartitioner partitioner,
                                EdgeDomainBlacklist blacklist)
    {
        this.block = block;
        this.bucketId = bucketId;
        this.tmpFileDir = tmpFileDir;
        this.inputFile = inputFile;
        this.outputFileWords = outputFileWords;
        this.outputFileUrls = outputFileUrls;
        this.partitioner = partitioner;
        this.blacklist = blacklist;
    }

    public void convert() throws IOException {
        Files.deleteIfExists(outputFileWords.toPath());
        Files.deleteIfExists(outputFileUrls.toPath());

        SearchIndexJournalReader journalReader = new SearchIndexJournalReader(MultimapFileLong.forReading(inputFile.toPath()));

        if (journalReader.fileHeader.fileSize() <= SearchIndexJournalReader.FILE_HEADER_SIZE_BYTES) {
            return;
        }

        logger.info("Converting {} ({}) {} {}", block.id, block, inputFile, journalReader.fileHeader);

        var lock = partitioner.getReadLock();
        try {
            lock.lock();

            var tmpUrlsFile = Files.createTempFile(tmpFileDir, "urls-sorted", ".dat");

            logger.info("Creating word index table {} for block {} ({})", outputFileWords, block.id, block);
            WordIndexOffsetsTable wordIndexTable = createWordIndexTable(journalReader, outputFileWords);

            logger.info("Creating word urls table {} for block {} ({})", outputFileUrls, block.id, block);
            createUrlTable(journalReader, tmpUrlsFile, wordIndexTable);

            Files.delete(tmpUrlsFile);
        }
        catch (IOException ex) {
            logger.error("Failed to convert", ex);
            throw ex;
        }
        finally {
            lock.unlock();
        }
    }

    private WordIndexOffsetsTable createWordIndexTable(SearchIndexJournalReader journalReader,
                                                       File outputFileWords) throws IOException
    {
        final int topWord = (int) journalReader.fileHeader.wordCount();

        WordsTableWriter wordsTableWriter = new WordsTableWriter(topWord);

        for (var entry : journalReader) {
            if (!isRelevantEntry(entry)) {
                continue;
            }

            final SearchIndexJournalEntry entryData = entry.readEntryUsingBuffer(tmpWordsBuffer);

            for (int i = 0; i < entryData.size(); i++) {
                int wordId = (int) entryData.get(i);
                if (wordId < 0 || wordId >= topWord) {
                    logger.warn("Bad wordId {}", wordId);
                }
                wordsTableWriter.acceptWord(wordId);
            }
        }

        wordsTableWriter.write(outputFileWords);

        return wordsTableWriter.getTable();
    }

    private void createUrlTable(SearchIndexJournalReader journalReader,
                                Path tmpUrlsFile,
                                WordIndexOffsetsTable wordOffsetsTable) throws IOException
    {
        long numberOfWordsTotal = 0;
        for (var entry : journalReader) {
            if (isRelevantEntry(entry))
                numberOfWordsTotal += entry.wordCount();
        }

        try (RandomAccessFile urlsTmpFileRAF = new RandomAccessFile(tmpUrlsFile.toFile(), "rw");
             FileChannel urlsTmpFileChannel = urlsTmpFileRAF.getChannel()) {

            try (RandomWriteFunnel rwf = new RandomWriteFunnel(tmpFileDir, numberOfWordsTotal, 10_000_000)) {
                int[] wordWriteOffset = new int[wordOffsetsTable.length()];

                for (var entry : journalReader) {
                    if (!isRelevantEntry(entry)) continue;

                    var entryData = entry.readEntryUsingBuffer(tmpWordsBuffer);

                    for (int i = 0; i < entryData.size(); i++) {
                        int wordId = (int) entryData.get(i);

                        if (wordId >= wordWriteOffset.length)
                            continue;
                        if (wordId < 0) {
                            logger.warn("Negative wordId {}", wordId);
                        }

                        final long urlInternal = translateUrl(entry.docId());
                        if (wordId > 0) {
                            rwf.put(wordOffsetsTable.get(wordId - 1) + wordWriteOffset[wordId]++, urlInternal);
                        } else {
                            rwf.put(wordWriteOffset[wordId]++, urlInternal);
                        }
                    }
                }

                rwf.write(urlsTmpFileChannel);
            }

            urlsTmpFileChannel.force(false);

            try (var urlsTmpFileMap = MultimapFileLong.forOutput(tmpUrlsFile, numberOfWordsTotal)) {
                if (wordOffsetsTable.length() > 0) {
                    var urlTmpFileSorter = urlsTmpFileMap.createSorter(tmpFileDir, internalSortLimit);

                    wordOffsetsTable.forEachRange(urlTmpFileSorter::sort);

                    urlsTmpFileMap.force();
                } else {
                    logger.warn("urls table empty -- nothing to sort");
                }
            }

            try (var urlsFileMap = MultimapFileLong.forOutput(outputFileUrls.toPath(), numberOfWordsTotal)) {
                var writer = new BTreeWriter(urlsFileMap, urlsBTreeContext);

                wordOffsetsTable.foldRanges((accumulatorIdx, start, length) -> {
                    // Note: The return value is accumulated into accumulatorIdx!

                    return writer.write(accumulatorIdx, length,
                            slice -> slice.transferFromFileChannel(urlsTmpFileChannel, 0, start, start + length));
                });

            } catch (Exception e) {
                logger.error("Error while writing BTree", e);
            }

        }
    }

    private long translateUrl(long url) {
        int domainId = partitioner.translateId(bucketId, (int) (url >>> 32));
        return ((long)domainId << 32) | (url & 0xFFFFFFFFL);
    }

    private boolean isRelevantEntry(SearchIndexJournalReader.JournalEntry entry) {
        return block.equals(entry.header.block())
                && !blacklist.isBlacklisted(entry.domainId())
                && partitioner.filterUnsafe(entry.domainId(), bucketId);
    }

}


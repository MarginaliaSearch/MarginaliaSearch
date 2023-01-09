package nu.marginalia.wmsa.edge.index.postings.reverse;

import nu.marginalia.util.RandomWriteFunnel;
import nu.marginalia.util.array.IntArray;
import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.array.algo.SortingContext;
import nu.marginalia.util.array.functional.LongBinaryIOOperation;
import nu.marginalia.util.array.functional.LongIOTransformer;
import nu.marginalia.util.array.functional.LongTransformer;
import nu.marginalia.util.btree.BTreeWriter;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalStatistics;
import nu.marginalia.wmsa.edge.index.postings.journal.reader.SearchIndexJournalReader;
import nu.marginalia.wmsa.edge.index.postings.journal.reader.SearchIndexJournalReaderSingleFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static nu.marginalia.wmsa.edge.index.postings.reverse.ReverseIndexParameters.ENTRY_SIZE;
import static nu.marginalia.wmsa.edge.index.postings.reverse.ReverseIndexParameters.bTreeContext;

public class ReverseIndexConverter {
    private static final int RWF_BIN_SIZE = 10_000_000;

    private final Path tmpFileDir;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SearchIndexJournalReaderSingleFile journalReader;
    private final Path outputFileWords;
    private final Path outputFileDocs;


    public ReverseIndexConverter(Path tmpFileDir,
                                 SearchIndexJournalReaderSingleFile journalReader,
                                 Path outputFileWords,
                                 Path outputFileDocs) {
        this.tmpFileDir = tmpFileDir;
        this.journalReader = journalReader;
        this.outputFileWords = outputFileWords;
        this.outputFileDocs = outputFileDocs;
    }

    public void convert() throws IOException {
        deleteOldFiles();

        if (journalReader.fileHeader.fileSize() <= SearchIndexJournalReader.FILE_HEADER_SIZE_BYTES) {
            return;
        }

        final SearchIndexJournalStatistics statistics = journalReader.getStatistics();

        final Path intermediateUrlsFile = Files.createTempFile(tmpFileDir, "urls-sorted", ".dat");
        SortingContext sortingContext = new SortingContext(tmpFileDir, 64_000);

        try {
            final long wordsFileSize = statistics.highestWord() + 1;

            logger.debug("Words file size: {}", wordsFileSize);
            // Create a count of how many documents has contains each word
            final LongArray wordsOffsets = LongArray.allocate(wordsFileSize);

            logger.info("Gathering Offsets");
            journalReader.forEachWordId(wordsOffsets::increment);
            wordsOffsets.transformEach(0, wordsFileSize, new CountToOffsetTransformer());

            // Construct an intermediate representation of the reverse documents index
            try (FileChannel intermediateDocChannel =
                         (FileChannel) Files.newByteChannel(intermediateUrlsFile,
                                 StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE))
            {
                logger.info("Creating Intermediate Docs File");

                // Construct intermediate index
                try (RandomWriteFunnel intermediateDocumentWriteFunnel = new RandomWriteFunnel(tmpFileDir, RWF_BIN_SIZE);
                     IntermediateIndexConstructor intermediateIndexConstructor = new IntermediateIndexConstructor(tmpFileDir, wordsOffsets, intermediateDocumentWriteFunnel)
                )
                {
                    journalReader.forEachDocIdRecord(intermediateIndexConstructor);
                    intermediateDocumentWriteFunnel.write(intermediateDocChannel);
                }
                intermediateDocChannel.force(false);

                logger.info("Sorting Intermediate Docs File");

                // Sort each segment of the intermediate file
                {
                    LongArray intermediateDocs = LongArray.mmapForWriting(intermediateUrlsFile);
                    wordsOffsets.foldIO(0, 0, wordsFileSize, (s, e) -> {
                        intermediateDocs.sortLargeSpanN(sortingContext, ENTRY_SIZE, s, e);
                        return e;
                    });
                    intermediateDocs.force();
                }


                logger.info("Sizing");

                SizeEstimator sizeEstimator = new SizeEstimator();
                wordsOffsets.foldIO(0, 0, wordsOffsets.size(), sizeEstimator);

                logger.info("Finalizing Docs File");

                LongArray finalDocs = LongArray.mmapForWriting(outputFileDocs, sizeEstimator.size);
                // Construct the proper reverse index
                wordsOffsets.transformEachIO(0, wordsOffsets.size(), new CreateReverseIndexBTreeTransformer(finalDocs, intermediateDocChannel));
                wordsOffsets.write(outputFileWords);

                // Attempt to clean up before forcing (important disk space preservation)
                Files.deleteIfExists(intermediateUrlsFile);

                wordsOffsets.force();
                finalDocs.force();
                logger.info("Done");
            }

        } catch (IOException ex) {
            logger.error("Failed to convert", ex);
            throw ex;
        } finally {
            Files.deleteIfExists(intermediateUrlsFile);
        }
    }

    private static class SizeEstimator implements LongBinaryIOOperation {
        public long size = 0;
        @Override
        public long apply(long start, long end) throws IOException {
            if (end == start) return end;

            size += bTreeContext.calculateSize((int) (end - start) / ENTRY_SIZE);

            return end;
        }
    }

    private void deleteOldFiles() throws IOException {
        Files.deleteIfExists(outputFileWords);
        Files.deleteIfExists(outputFileDocs);
    }

    private static class CountToOffsetTransformer implements LongTransformer {
        long offset = 0;

        @Override
        public long transform(long pos, long count) {
            return (offset += ENTRY_SIZE * count);
        }
    }

    private static class CreateReverseIndexBTreeTransformer implements LongIOTransformer {
        private final BTreeWriter writer;
        private final FileChannel intermediateChannel;

        long start = 0;
        long writeOffset = 0;

        public CreateReverseIndexBTreeTransformer(LongArray urlsFileMap, FileChannel intermediateChannel) {
            this.writer = new BTreeWriter(urlsFileMap, ReverseIndexParameters.bTreeContext);
            this.intermediateChannel = intermediateChannel;
        }

        @Override
        public long transform(long pos, long end) throws IOException {

            assert (end - start) % ReverseIndexParameters.ENTRY_SIZE == 0;

            final int size = (int)(end - start) / ReverseIndexParameters.ENTRY_SIZE;

            if (size == 0) {
                return -1;
            }

            final long offsetForBlock = writeOffset;

            writeOffset += writer.write(writeOffset, size,
                    mapRegion -> mapRegion.transferFrom(intermediateChannel, start, 0, end - start)
            );

            start = end;
            return offsetForBlock;
        }
    }

    private static class IntermediateIndexConstructor implements SearchIndexJournalReaderSingleFile.LongObjectConsumer<SearchIndexJournalEntry.Record>, AutoCloseable {

        private final LongArray wordRangeEnds;
        private final IntArray wordRangeOffset;
        private final RandomWriteFunnel documentsFile;

        private final Path tempFile;

        public IntermediateIndexConstructor(Path tempDir, LongArray wordRangeEnds, RandomWriteFunnel documentsFile) throws IOException {
            tempFile = Files.createTempFile(tempDir, "iic", "dat");

            this.wordRangeEnds = wordRangeEnds;
            this.wordRangeOffset = IntArray.mmapForWriting(tempFile, wordRangeEnds.size());
            this.documentsFile = documentsFile;
        }

        @Override
        public void accept(long docId, SearchIndexJournalEntry.Record record) {
            final long urlId = docId & 0xFFFF_FFFFL;
            final int wordId = record.wordId();

            long offset = startOfRange(wordId);

            documentsFile.put(offset + wordRangeOffset.getAndIncrement(wordId), urlId);
            documentsFile.put(offset + wordRangeOffset.getAndIncrement(wordId), record.metadata());

        }

        private long startOfRange(int wordId) {
            if (wordId == 0) return 0;

            return wordRangeEnds.get(wordId - 1);
        }

        public void close() throws IOException {
            Files.delete(tempFile);
        }
    }

}


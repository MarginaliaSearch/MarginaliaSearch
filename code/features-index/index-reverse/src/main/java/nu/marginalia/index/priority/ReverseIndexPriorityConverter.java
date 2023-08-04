package nu.marginalia.index.priority;

import lombok.SneakyThrows;
import nu.marginalia.array.IntArray;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.algo.SortingContext;
import nu.marginalia.index.construction.CountToOffsetTransformer;
import nu.marginalia.index.construction.ReverseIndexBTreeTransformer;
import nu.marginalia.index.construction.IndexSizeEstimator;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalStatistics;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.ranking.DomainRankings;
import nu.marginalia.rwf.RandomWriteFunnel;
import nu.marginalia.service.control.ServiceHeartbeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static nu.marginalia.index.priority.ReverseIndexPriorityParameters.bTreeContext;

public class ReverseIndexPriorityConverter {
    private static final int RWF_BIN_SIZE = 10_000_000;

    private final ServiceHeartbeat heartbeat;
    private final Path tmpFileDir;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final IndexJournalReader journalReader;
    private final DomainRankings domainRankings;
    private final Path outputFileWords;
    private final Path outputFileDocs;
    private final SortingContext sortingContext;

    public ReverseIndexPriorityConverter(ServiceHeartbeat heartbeat,
                                         Path tmpFileDir,
                                         IndexJournalReader journalReader,
                                         DomainRankings domainRankings,
                                         Path outputFileWords,
                                         Path outputFileDocs) {
        this.heartbeat = heartbeat;
        this.tmpFileDir = tmpFileDir;
        this.journalReader = journalReader;
        this.domainRankings = domainRankings;
        this.outputFileWords = outputFileWords;
        this.outputFileDocs = outputFileDocs;
        this.sortingContext = new SortingContext(tmpFileDir, 64_000);
    }

    public enum TaskSteps {
        ACCUMULATE_STATISTICS,
        INCREMENT_OFFSETS,
        COUNT_OFFSETS,
        CREATE_INTERMEDIATE_DOCS,
        SORT_INTERMEDIATE_DOCS,
        SIZING,
        FINALIZING_DOCS,
        FORCE,
        FINISHED,
    }

    public void convert() throws IOException {
        deleteOldFiles();

        if (journalReader.fileHeader().fileSize() <= IndexJournalReader.FILE_HEADER_SIZE_BYTES) {
            logger.warn("Bailing: Journal is empty!");
            return;
        }

        final Path intermediateUrlsFile = Files.createTempFile(tmpFileDir, "urls-sorted", ".dat");

        try (var progress = heartbeat.createServiceProcessHeartbeat(TaskSteps.class, "reverseIndexPriorityConverter")) {
            progress.progress(TaskSteps.ACCUMULATE_STATISTICS);

            final IndexJournalStatistics statistics = journalReader.getStatistics();
            final long wordsFileSize = statistics.highestWord() + 1;

            progress.progress(TaskSteps.INCREMENT_OFFSETS);

            logger.debug("Words file size: {}", wordsFileSize);
            // Create a count of how many documents has contains each word
            final LongArray wordsOffsets = LongArray.allocate(wordsFileSize);

            journalReader.forEachWordId(wordsOffsets::increment);
            progress.progress(TaskSteps.COUNT_OFFSETS);

            wordsOffsets.transformEach(0, wordsFileSize, new CountToOffsetTransformer(ReverseIndexPriorityParameters.ENTRY_SIZE));

            progress.progress(TaskSteps.CREATE_INTERMEDIATE_DOCS);

            // Construct an intermediate representation of the reverse documents index
            try (FileChannel intermediateDocChannel =
                         (FileChannel) Files.newByteChannel(intermediateUrlsFile,
                                 StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE))
            {

                // Construct intermediate index
                try (RandomWriteFunnel intermediateDocumentWriteFunnel = new RandomWriteFunnel(tmpFileDir, RWF_BIN_SIZE);
                     IntermediateIndexConstructor intermediateIndexConstructor = new IntermediateIndexConstructor(tmpFileDir, wordsOffsets, intermediateDocumentWriteFunnel)
                )
                {
                    journalReader.forEachDocIdRecord(intermediateIndexConstructor);
                    intermediateDocumentWriteFunnel.write(intermediateDocChannel);
                }
                intermediateDocChannel.force(false);
                progress.progress(TaskSteps.SORT_INTERMEDIATE_DOCS);

                // Sort each segment of the intermediate file
                {
                    LongArray intermediateDocs = LongArray.mmapForModifying(intermediateUrlsFile);
                    wordsOffsets.foldIO(0, 0, wordsFileSize, (s, e) -> {
                        intermediateDocs.sortLargeSpan(sortingContext, s, e);
                        return e;
                    });
                    intermediateDocs.force();
                }

                progress.progress(TaskSteps.SIZING);

                IndexSizeEstimator sizeEstimator = new IndexSizeEstimator(
                        bTreeContext,
                        ReverseIndexPriorityParameters.ENTRY_SIZE);

                wordsOffsets.fold(0, 0, wordsOffsets.size(), sizeEstimator);
                progress.progress(TaskSteps.FINALIZING_DOCS);

                LongArray finalDocs = LongArray.mmapForWriting(outputFileDocs, sizeEstimator.size);
                // Construct the proper reverse index
                wordsOffsets.transformEachIO(0, wordsOffsets.size(), new ReverseIndexBTreeTransformer(finalDocs, ReverseIndexPriorityParameters.ENTRY_SIZE, bTreeContext, intermediateDocChannel));
                wordsOffsets.write(outputFileWords);

                progress.progress(TaskSteps.FORCE);

                // Attempt to clean up before forcing (important disk space preservation)
                Files.deleteIfExists(intermediateUrlsFile);

                wordsOffsets.force();
                finalDocs.force();

                progress.progress(TaskSteps.FINISHED);
            }

        } catch (IOException ex) {
            logger.error("Failed to convert", ex);
            throw ex;
        } finally {
            Files.deleteIfExists(intermediateUrlsFile);
        }
    }

    private void deleteOldFiles() throws IOException {
        Files.deleteIfExists(outputFileWords);
        Files.deleteIfExists(outputFileDocs);
    }

    private class IntermediateIndexConstructor implements IndexJournalReader.LongObjectConsumer<IndexJournalEntryData.Record>, AutoCloseable {

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

        @SneakyThrows
        @Override
        public void accept(long docId, IndexJournalEntryData.Record record) {

            /* Encode the ID as
             *
             *     32 bits  32 bits
             *   [ ranking | url-id ]
             *
             *  in order to get low-ranking documents to be considered first
             *  when sorting the items.
             */

            int domainId = (int) (docId >>> 32);
            long rankingId = (long) domainRankings.getRanking(domainId) << 32;

            int urlId = (int) (docId & 0xFFFF_FFFFL);
            long rankEncodedId = rankingId | urlId;

            final int wordId = record.wordId();
            long offset = startOfRange(wordId);

            documentsFile.put(offset + wordRangeOffset.getAndIncrement(wordId), rankEncodedId);
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


package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.index.reverse.construction.PositionsFileConstructor;
import nu.marginalia.process.control.ProcessHeartbeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public class FullIndexConstructor {

    private static final Logger logger = LoggerFactory.getLogger(FullIndexConstructor.class);

    public enum CreateReverseIndexSteps {
        CONSTRUCT,
        FINALIZE,
        FINISHED
    }

    private final Path outputFileDocs;
    private final Path outputFileDocsValues;
    private final Path outputFileWords;
    private final Path outputFilePositions;
    private final DocIdRewriter docIdRewriter;
    private final Path tmpDir;

    public FullIndexConstructor( Path outputFileDocs,
                                Path outputFileDocsValues,
                                Path outputFileWords,
                                Path outputFilePositions,
                                DocIdRewriter docIdRewriter,
                                Path tmpDir) {
        this.outputFileDocs = outputFileDocs;
        this.outputFileDocsValues = outputFileDocsValues;
        this.outputFileWords = outputFileWords;
        this.outputFilePositions = outputFilePositions;
        this.docIdRewriter = docIdRewriter;
        this.tmpDir = tmpDir;
    }

    public void createReverseIndex(ProcessHeartbeat processHeartbeat,
                                   String processName,
                                   IndexJournal journal) throws IOException
    {
        try (var heartbeat = processHeartbeat.createProcessTaskHeartbeat(CreateReverseIndexSteps.class, processName);
             var preindexHeartbeat = processHeartbeat.createAdHocTaskHeartbeat("constructPreindexes");
             var posConstructor = new PositionsFileConstructor(outputFilePositions)
        ) {
            heartbeat.progress(CreateReverseIndexSteps.CONSTRUCT);

            AtomicInteger progress = new AtomicInteger(0);

            var journalVersions = journal.pages();

            journalVersions
                .parallelStream()
                .map(in -> {
                    preindexHeartbeat.progress("PREINDEX/MERGE", progress.incrementAndGet(), journalVersions.size());
                    return construct(in, posConstructor);
                })
                .reduce(this::merge)
                .ifPresent((index) -> {
                    heartbeat.progress(CreateReverseIndexSteps.FINALIZE);
                    finalizeIndex(index);
                    heartbeat.progress(CreateReverseIndexSteps.FINISHED);
                });

            heartbeat.progress(CreateReverseIndexSteps.FINISHED);
        }
    }

    private FullPreindexReference construct(IndexJournalPage journalInstance, PositionsFileConstructor positionsFileConstructor) {
        try {
            return FullPreindex
                    .constructPreindex(journalInstance, positionsFileConstructor, docIdRewriter, tmpDir)
                    .closeToReference();
        }
        catch (IOException e) {
            logger.error("Error constructing preindex", e);
            throw new RuntimeException(e);
        }
    }

    private FullPreindexReference merge(FullPreindexReference leftR, FullPreindexReference rightR) {
        try {
            var left = leftR.open();
            var right = rightR.open();

            try {
                return FullPreindex.merge(tmpDir, left, right).closeToReference();
            } finally {
                left.delete();
                right.delete();
            }
        }
        catch (IOException e) {
            logger.error("Error merging preindex", e);
            throw new RuntimeException(e);
        }


    }

    private void finalizeIndex(FullPreindexReference finalPR) {
        try {
            var finalP = finalPR.open();
            finalP.finalizeIndex(
                    outputFileDocs,
                    outputFileDocsValues,
                    outputFileWords
            );
            finalP.delete();
        }
        catch (IOException e) {
            logger.error("Error finalizing index", e);
            throw new RuntimeException(e);
        }
    }


}

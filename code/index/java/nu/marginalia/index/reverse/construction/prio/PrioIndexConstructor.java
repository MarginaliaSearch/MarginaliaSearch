package nu.marginalia.index.reverse.construction.prio;

import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.process.control.ProcessHeartbeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public class PrioIndexConstructor {

    private static final Logger logger = LoggerFactory.getLogger(PrioIndexConstructor.class);

    public enum CreateReverseIndexSteps {
        CONSTRUCT,
        FINALIZE,
        FINISHED
    }

    private final Path outputFileDocs;
    private final Path outputFileWords;
    private final DocIdRewriter docIdRewriter;
    private final Path tmpDir;

    public PrioIndexConstructor(Path outputFileDocs,
                                Path outputFileWords,
                                DocIdRewriter docIdRewriter,
                                Path tmpDir) {
        this.outputFileDocs = outputFileDocs;
        this.outputFileWords = outputFileWords;
        this.docIdRewriter = docIdRewriter;
        this.tmpDir = tmpDir;
    }

    public void createReverseIndex(ProcessHeartbeat processHeartbeat,
                                   String processName,
                                   IndexJournal journal,
                                   Path sourceBaseDir) throws IOException
    {
        try (var heartbeat = processHeartbeat.createProcessTaskHeartbeat(CreateReverseIndexSteps.class, processName);
             var preindexHeartbeat = processHeartbeat.createAdHocTaskHeartbeat("constructPreindexes")
        ) {
            heartbeat.progress(CreateReverseIndexSteps.CONSTRUCT);

            AtomicInteger progress = new AtomicInteger(0);

            var journalVersions = journal.pages();

            journalVersions
                .parallelStream()
                .map(in -> {
                    preindexHeartbeat.progress("PREINDEX/MERGE", progress.incrementAndGet(), journalVersions.size());
                    return construct(in);
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

    private PrioPreindexReference construct(IndexJournalPage journalInstance) {
        try {
            return PrioPreindex
                    .constructPreindex(journalInstance, docIdRewriter, tmpDir)
                    .closeToReference();
        }
        catch (IOException ex) {
            logger.error("Failed to construct preindex", ex);
            throw new RuntimeException(ex);
        }
    }

    private PrioPreindexReference merge(PrioPreindexReference leftR, PrioPreindexReference rightR) {
        try {
            var left = leftR.open();
            var right = rightR.open();

            try {
                return PrioPreindex.merge(tmpDir, left, right).closeToReference();
            } finally {
                left.delete();
                right.delete();
            }
        }
        catch (IOException ex) {
            logger.error("Failed to merge preindex", ex);
            throw new RuntimeException(ex);
        }

    }

    private void finalizeIndex(PrioPreindexReference finalPR) {
        try {
            var finalP = finalPR.open();
            finalP.finalizeIndex(outputFileDocs, outputFileWords);
            finalP.delete();
        }
        catch (IOException ex) {
            logger.error("Failed to finalize preindex", ex);
            throw new RuntimeException(ex);
        }
    }


}

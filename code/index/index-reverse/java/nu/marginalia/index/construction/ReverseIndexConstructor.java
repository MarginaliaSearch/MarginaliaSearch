package nu.marginalia.index.construction;

import lombok.SneakyThrows;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.index.journal.IndexJournalFileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public class ReverseIndexConstructor {

    private static final Logger logger = LoggerFactory.getLogger(ReverseIndexConstructor.class);

    public enum CreateReverseIndexSteps {
        CONSTRUCT,
        FINALIZE,
        FINISHED
    }

    private final Path outputFileDocs;
    private final Path outputFileWords;
    private final Path outputFilePositions;
    private final JournalReaderSource readerSource;
    private final DocIdRewriter docIdRewriter;
    private final Path tmpDir;

    public ReverseIndexConstructor(Path outputFileDocs,
                                   Path outputFileWords,
                                   Path outputFilePositions,
                                   JournalReaderSource readerSource,
                                   DocIdRewriter docIdRewriter,
                                   Path tmpDir) {
        this.outputFileDocs = outputFileDocs;
        this.outputFileWords = outputFileWords;
        this.outputFilePositions = outputFilePositions;
        this.readerSource = readerSource;
        this.docIdRewriter = docIdRewriter;
        this.tmpDir = tmpDir;
    }

    public void createReverseIndex(ProcessHeartbeat processHeartbeat,
                                   String processName,
                                   Path sourceBaseDir) throws IOException
    {
        var inputs = IndexJournalFileNames.findJournalFiles(sourceBaseDir);
        if (inputs.isEmpty()) {
            logger.error("No journal files in base dir {}", sourceBaseDir);
            return;
        }

        try (var heartbeat = processHeartbeat.createProcessTaskHeartbeat(CreateReverseIndexSteps.class, processName);
             var preindexHeartbeat = processHeartbeat.createAdHocTaskHeartbeat("constructPreindexes");
             var posConstructor = new PositionsFileConstructor(outputFilePositions)
        ) {
            heartbeat.progress(CreateReverseIndexSteps.CONSTRUCT);

            AtomicInteger progress = new AtomicInteger(0);

            inputs
                .parallelStream()
                .map(in -> {
                    preindexHeartbeat.progress("PREINDEX/MERGE", progress.incrementAndGet(), inputs.size());
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

    @SneakyThrows
    private ReversePreindexReference construct(Path input, PositionsFileConstructor positionsFileConstructor) {
        return ReversePreindex
                .constructPreindex(readerSource.construct(input), positionsFileConstructor, docIdRewriter, tmpDir)
                .closeToReference();
    }

    @SneakyThrows
    private ReversePreindexReference merge(ReversePreindexReference leftR, ReversePreindexReference rightR) {

        var left = leftR.open();
        var right = rightR.open();

        try {
            return ReversePreindex.merge(tmpDir, left, right).closeToReference();
        }
        finally {
            left.delete();
            right.delete();
        }


    }

    @SneakyThrows
    private void finalizeIndex(ReversePreindexReference finalPR) {
        var finalP = finalPR.open();
        finalP.finalizeIndex(outputFileDocs, outputFileWords);
        finalP.delete();
    }


}

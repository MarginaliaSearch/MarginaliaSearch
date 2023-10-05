package nu.marginalia.index.construction;

import nu.marginalia.process.control.ProcessAdHocTaskHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ReverseIndexConstructor {

    private static final Logger logger = LoggerFactory.getLogger(ReverseIndexConstructor.class);

    public enum CreateReverseIndexSteps {
        CREATE_PREINDEXES,
        MERGE_PREINDEXES,
        FINALIZE,
        FINISHED
    }
    public static void createReverseIndex(
                                    ProcessHeartbeat processHeartbeat,
                                    JournalReaderSource readerSource,
                                    Path sourceBaseDir,
                                    DocIdRewriter docIdRewriter,
                                    Path tmpDir,
                                    Path outputFileDocs,
                                    Path outputFileWords) throws IOException
    {
        var inputs = IndexJournalFileNames.findJournalFiles(sourceBaseDir);
        if (inputs.isEmpty()) {
            logger.error("No journal files in base dir {}", sourceBaseDir);
            return;
        }

        try (var heartbeat = processHeartbeat.createProcessTaskHeartbeat(CreateReverseIndexSteps.class, "createReverseIndex")) {
            List<ReversePreindexReference> preindexes = new ArrayList<>();

            heartbeat.progress(CreateReverseIndexSteps.CREATE_PREINDEXES);

            try (var preindexHeartbeat = processHeartbeat.createAdHocTaskHeartbeat("constructPreindexes")) {

                AtomicInteger progress = new AtomicInteger(0);
                inputs.parallelStream().map(input ->
                        {
                            try {
                                return ReversePreindex
                                        .constructPreindex(readerSource.construct(input), docIdRewriter, tmpDir)
                                        .closeToReference();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                ).peek(i -> {
                    preindexHeartbeat.progress("PREINDEX", progress.incrementAndGet(), inputs.size());
                }).forEach(preindexes::add);

                preindexHeartbeat.progress("FINISHED", inputs.size(), inputs.size());
            }

            heartbeat.progress(CreateReverseIndexSteps.MERGE_PREINDEXES);
            ReversePreindex finalPreindex = null;

            try (var mergeHeartbeat = processHeartbeat.createAdHocTaskHeartbeat("mergePreindexes")) {
                finalPreindex = mergePreindexes(tmpDir, mergeHeartbeat, preindexes)
                        .open();

                heartbeat.progress(CreateReverseIndexSteps.FINALIZE);
                finalPreindex.finalizeIndex(outputFileDocs, outputFileWords);
            }
            finally {
                if (null != finalPreindex)
                    finalPreindex.delete();
            }

            heartbeat.progress(CreateReverseIndexSteps.FINISHED);
        }
    }

    private static ReversePreindexReference mergePreindexes(Path workDir,
                                                   ProcessAdHocTaskHeartbeat mergeHeartbeat,
                                                   List<ReversePreindexReference> preindexes) throws IOException {
        assert !preindexes.isEmpty();

        if (preindexes.size() == 1) {
            logger.info("Single preindex, no merge necessary");
            return preindexes.get(0);
        }

        return preindexes.parallelStream().reduce((l, r) -> {
            try {
                var left = l.open();
                var right = r.open();

                var ret = ReversePreindex
                        .merge(workDir, left, right)
                        .closeToReference();

                left.delete();
                right.delete();

                return ret;
            }
            catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }).orElseThrow(IllegalStateException::new);
    }

}

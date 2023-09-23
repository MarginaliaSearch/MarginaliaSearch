package nu.marginalia.index.construction;

import nu.marginalia.process.control.ProcessAdHocTaskHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

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
                for (int i = 0; i < inputs.size(); i++) {
                    var input = inputs.get(i);

                    preindexHeartbeat.progress(input.toFile().getName(), i, inputs.size());

                    preindexes.add(
                        ReversePreindex
                            .constructPreindex(readerSource.construct(input), docIdRewriter, tmpDir)
                            .closeToReference()
                    );
                }

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

        LinkedList<ReversePreindexReference> toMerge = new LinkedList<>(preindexes);
        List<ReversePreindexReference> mergedItems = new ArrayList<>(preindexes.size() / 2);

        int pass = 0;
        while (toMerge.size() > 1) {
            String stage = String.format("PASS[%d]: %d -> %d", ++pass, toMerge.size(), toMerge.size()/2 + (toMerge.size() % 2));

            int totalToMergeCount = toMerge.size()/2;
            int toMergeProgress = 0;

            while (toMerge.size() >= 2) {
                mergeHeartbeat.progress(stage, toMergeProgress++, totalToMergeCount);

                var left = toMerge.removeFirst().open();
                var right = toMerge.removeFirst().open();

                mergedItems.add(
                    ReversePreindex
                            .merge(workDir, left, right)
                            .closeToReference()
                );

                left.delete();
                right.delete();
            }

            // Pour the merged items back in the toMerge queue
            // (note, toMerge may still have a single item in it,
            // in the case where it had an odd population)
            toMerge.addAll(mergedItems);
            mergedItems.clear();
        }

        mergeHeartbeat.progress("FINISHED", 1, 1);

        return toMerge.getFirst();
    }

}

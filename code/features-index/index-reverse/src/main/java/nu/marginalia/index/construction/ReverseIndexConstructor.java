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
            List<ReversePreindex> preindexes = new ArrayList<>();

            heartbeat.progress(CreateReverseIndexSteps.CREATE_PREINDEXES);

            try (var preindexHeartbeat = processHeartbeat.createAdHocTaskHeartbeat("constructPreindexes")) {
                for (int i = 0; i < inputs.size(); i++) {
                    var input = inputs.get(i);

                    preindexHeartbeat.progress(input.toFile().getName(), i, inputs.size());

                    preindexes.add(ReversePreindex.constructPreindex(readerSource.construct(input), docIdRewriter, tmpDir));
                }

                preindexHeartbeat.progress("FINISHED", inputs.size(), inputs.size());
            }

            heartbeat.progress(CreateReverseIndexSteps.MERGE_PREINDEXES);
            ReversePreindex finalPreindex;

            try (var mergeHeartbeat = processHeartbeat.createAdHocTaskHeartbeat("mergePreindexes")) {
                finalPreindex = mergePreindexes(tmpDir, mergeHeartbeat, preindexes);
            }

            heartbeat.progress(CreateReverseIndexSteps.FINALIZE);
            finalPreindex.finalizeIndex(outputFileDocs, outputFileWords);

            heartbeat.progress(CreateReverseIndexSteps.FINISHED);
            finalPreindex.delete();
        }
    }

    private static ReversePreindex mergePreindexes(Path workDir, ProcessAdHocTaskHeartbeat mergeHeartbeat, List<ReversePreindex> preindexes) throws IOException {
        assert !preindexes.isEmpty();

        if (preindexes.size() == 1) {
            logger.info("Single preindex, no merge necessary");
            return preindexes.get(0);
        }

        List<ReversePreindex> toMerge = new ArrayList<>(preindexes);
        List<ReversePreindex> merged = new ArrayList<>();

        int pass = 0;
        while (toMerge.size() != 1) {
            String stage = String.format("PASS[%d]: %d -> %d", ++pass,
                    toMerge.size(),
                    toMerge.size()/2 + (toMerge.size() % 2)
            );

            for (int i = 0; i + 1 < toMerge.size(); i+=2) {
                mergeHeartbeat.progress(stage, i/2, toMerge.size()/2);

                var left = toMerge.get(i);
                var right = toMerge.get(i+1);

                merged.add(ReversePreindex.merge(workDir, left, right));

                left.delete();
                right.delete();
            }

            if ((toMerge.size() % 2) != 0) {
                merged.add(toMerge.get(toMerge.size()-1));
            }

            toMerge.clear();
            toMerge.addAll(merged);
            merged.clear();
        }

        mergeHeartbeat.progress("FINISHED", 1, 1);

        return toMerge.get(0);
    }

}

package nu.marginalia.index.construction;

import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ReverseIndexConstructor {

    private static final Logger logger = LoggerFactory.getLogger(ReverseIndexConstructor.class);

    public static void createReverseIndex(
                                    JournalReaderSource readerSource,
                                    Path sourceBaseDir,
                                    Path tmpDir,
                                    Path outputFileDocs,
                                    Path outputFileWords) throws IOException
    {
        var inputs = IndexJournalFileNames.findJournalFiles(sourceBaseDir);
        if (inputs.isEmpty()) {
            logger.error("No journal files in base dir {}", sourceBaseDir);
            return;
        }

        List<ReversePreindex> preindexes = new ArrayList<>();

        for (var input : inputs) {
            logger.info("Construcing preindex from {}", input);
            var preindex = ReversePreindex.constructPreindex(readerSource.construct(input),
                    tmpDir, tmpDir);
            preindexes.add(preindex);
        }

        logger.info("Merging");
        var finalPreindex = mergePreindexes(tmpDir, preindexes);
        logger.info("Finalizing");
        finalPreindex.finalizeIndex(outputFileDocs, outputFileWords);
        logger.info("Done");
        finalPreindex.delete();
    }

    private static ReversePreindex mergePreindexes(Path workDir, List<ReversePreindex> preindexes) throws IOException {
        assert !preindexes.isEmpty();

        if (preindexes.size() == 1) {
            logger.info("Single preindex, no merge necessary");
            return preindexes.get(0);
        }

        List<ReversePreindex> toMerge = new ArrayList<>(preindexes);
        List<ReversePreindex> merged = new ArrayList<>();

        while (toMerge.size() != 1) {
            for (int i = 0; i < toMerge.size(); i+=2) {
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

        return toMerge.get(0);
    }

}

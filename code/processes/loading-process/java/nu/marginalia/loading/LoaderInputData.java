package nu.marginalia.loading;

import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.worklog.BatchingWorkLogInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;


public class LoaderInputData {
    private final List<Path> sourceDirectories;
    private static final Logger logger = LoggerFactory.getLogger(LoaderInputData.class);
    private final Map<Path, Integer> lastGoodBatch = new HashMap<>();

    public LoaderInputData(List<Path> sourceDirectories) throws IOException {
        this.sourceDirectories = sourceDirectories;

        for (var source : sourceDirectories) {
            int lastGoodBatch = BatchingWorkLogInspector.getValidBatches(source.resolve("processor.log"));

            this.lastGoodBatch.put(source, lastGoodBatch);

            if (lastGoodBatch == 0) {
                // This is useful diagnostic information, so we log it as a warning
                logger.warn("No valid batches found in {}", source);
            }
        }

    }

    /** This constructor is primarily intended for testing.  It still works and is good though,
     * but it skips consulting processor.log for lastGoodBatch
     */
    public LoaderInputData(Path singleSource, int lastBatch) throws IOException {
        sourceDirectories = List.of(singleSource);
        lastGoodBatch.put(singleSource, lastBatch);
    }

    public Collection<Path> listDomainFiles() {
        List<Path> pathsAll = new ArrayList<>();
        for (var source : sourceDirectories) {
            pathsAll.addAll(ProcessedDataFileNames.listDomainFiles(source, lastGoodBatch.get(source)));
        }
        return pathsAll;
    }

    public Collection<Path> listDomainLinkFiles() {
        List<Path> pathsAll = new ArrayList<>();
        for (var source : sourceDirectories) {
            pathsAll.addAll(ProcessedDataFileNames.listDomainLinkFiles(source, lastGoodBatch.get(source)));
        }
        return pathsAll;
    }

    public Collection<Path> listDocumentFiles() {
        List<Path> pathsAll = new ArrayList<>();
        for (var source : sourceDirectories) {
            pathsAll.addAll(ProcessedDataFileNames.listDocumentFiles(source, lastGoodBatch.get(source)));
        }
        return pathsAll;
    }
}

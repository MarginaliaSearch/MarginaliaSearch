package nu.marginalia.loading;

import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.worklog.BatchingWorkLogInspector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;


public class LoaderInputData {
    private final List<Path> sourceDirectories;
    private final Map<Path, Integer> lastGoodBatch = new HashMap<>();

    public LoaderInputData(List<Path> sourceDirectories) throws IOException {
        this.sourceDirectories = sourceDirectories;

        for (var source : sourceDirectories) {
            lastGoodBatch.put(source, BatchingWorkLogInspector.getValidBatches(source.resolve("processor.log")));
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

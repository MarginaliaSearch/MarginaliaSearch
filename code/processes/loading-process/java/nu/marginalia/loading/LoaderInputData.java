package nu.marginalia.loading;

import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.model.processed.SlopDomainLinkRecord;
import nu.marginalia.model.processed.SlopDomainRecord;
import nu.marginalia.slop.SlopTable;
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

    public Collection<SlopTable.Ref<SlopDomainRecord>> listDomainPages() {
        List<SlopTable.Ref<SlopDomainRecord>> pathsAll = new ArrayList<>();

        for (var source : sourceDirectories) {
            for (int i = 0; i < lastGoodBatch.get(source); i++) {
                pathsAll.add(new SlopTable.Ref<>(ProcessedDataFileNames.domainFileName(source), i));
            }
        }
        return pathsAll;
    }

    public Collection<SlopTable.Ref<SlopDomainLinkRecord>> listDomainLinkPages() {
        List<SlopTable.Ref<SlopDomainLinkRecord>> pathsAll = new ArrayList<>();

        for (var source : sourceDirectories) {
            for (int i = 0; i < lastGoodBatch.get(source); i++) {
                pathsAll.add(new SlopTable.Ref<>(ProcessedDataFileNames.domainLinkFileName(source), i));
            }
        }
        return pathsAll;
    }

    public Collection<SlopTable.Ref<SlopDocumentRecord>> listDocumentFiles() {
        List<SlopTable.Ref<SlopDocumentRecord>> pathsAll = new ArrayList<>();

        for (var source : sourceDirectories) {
            for (int i = 0; i < lastGoodBatch.get(source); i++) {
                pathsAll.add(new SlopTable.Ref<>(ProcessedDataFileNames.documentFileName(source), i));
            }
        }
        return pathsAll;
    }
}

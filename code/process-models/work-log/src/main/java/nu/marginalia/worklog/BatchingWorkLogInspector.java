package nu.marginalia.worklog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BatchingWorkLogInspector {
    /** Batches up until the return value of this method
     * are considered valid.  If the method returns 2, then batches
     * 0 and 1 are good, etc.
     * <p></p>
     * Invariant: BatchingWorkLogInspector.getValidBatches() always
     * returns the same value as BatchingWorkLog.getBatchNumber()
     */
    public static int getValidBatches(Path file) throws IOException {
        try (var linesStream = Files.lines(file)) {
            return (int) linesStream.map(WorkLogItem::parse)
                    .filter(FinishBatch.class::isInstance)
                    .count();
        }
    }
}

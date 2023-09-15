package nu.marginalia.worklog;

import java.io.IOException;

/** The BatchingWorkLog is a work log for items of work performed in batches,
 * where each batch needs to be finalized before the items it consists of can be
 * considered done.  This is needed when the data is serialized into a format such
 * as Parquet, where disparate items go into the same file, and the writer needs to be
 * properly closed before the file can be read.
 */
public interface BatchingWorkLog extends AutoCloseable {

    /** Returns true if logItem(id) has been run,
     * and logFinishedBatch has been run after that.
     */
    boolean isItemCommitted(String id);

    /** Returns true if logItem(id) has been run
     * but not logFinishedBatch().
     * <p/>
     * Unlike isItemCommitted(), this state is ephemeral and not
     * retained if e.g. the process crashes and resumes.
     * */
    boolean isItemInCurrentBatch(String id);

    default boolean isItemProcessed(String id) {
        return isItemCommitted(id) || isItemInCurrentBatch(id);
    }

    /** Log additional item to the current batch */
    void logItem(String id) throws IOException;

    /** Mark the current batch as finished and increment
     * the batch number counter
     */
    void logFinishedBatch() throws IOException;

    int getBatchNumber();

    /** Returns false if logItem has been invoked since last logFinishedBatch */
    boolean isCurrentBatchEmpty();

    int size();
}

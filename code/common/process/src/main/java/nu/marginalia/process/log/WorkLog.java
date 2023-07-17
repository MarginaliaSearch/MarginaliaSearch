package nu.marginalia.process.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/** WorkLog is a journal of work done by a process,
 * so that it can be resumed after a crash or termination.
 * <p>
 * The log file itself is a tab-separated file with the following columns:
 * <ul>
 *     <li>Job ID</li>
 *     <li>Timestamp</li>
 *     <li>Location (e.g. path on disk)</li>
 *     <li>Size</li>
 * </p>
 *
 */
public class WorkLog implements AutoCloseable {
    private final Set<String> finishedJobs = new HashSet<>();
    private final FileOutputStream logWriter;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public WorkLog(Path logFile) throws IOException {
        if (Files.exists(logFile)) {
            try (var lines = Files.lines(logFile)) {
                lines.filter(WorkLogEntry::isJobId)
                        .map(WorkLogEntry::parseJobIdFromLogLine)
                        .forEach(finishedJobs::add);
            }
        }

        logWriter = new FileOutputStream(logFile.toFile(), true);
        writeLogEntry("# Starting WorkLog @ " + LocalDateTime.now() + "\n");
    }

    /** Create an iterable over the work log
     * <br>
     * <b>Caveat: </b> If the iterator is not iterated to the end,
     *                  it will leak a file descriptor.
     */
    public static Iterable<WorkLogEntry> iterable(Path logFile) {
        return new WorkLoadIterable<>(logFile, Optional::of);
    }

    /** Create an iterable over the work log, applying a mapping function to each item
     * <br>
     * <b>Caveat: </b> If the iterator is not iterated to the end,
     *                  it will leak a file descriptor.
     */
    public static <T> Iterable<T> iterableMap(Path logFile, Function<WorkLogEntry, Optional<T>> mapper) {
        return new WorkLoadIterable<>(logFile, mapper);
    }

    // Use synchro over concurrent set to avoid competing writes
    // - correct is better than fast here, it's sketchy enough to use
    // a PrintWriter

    /** Mark the job as finished in the work log
     *
     * @param id  job identifier
     * @param where  free form field, e.g. location on disk
     * @param size  free form field, e.g. how many items were processed
     */
    public synchronized void setJobToFinished(String id, String where, int size) throws IOException {
        if (!finishedJobs.add(id)) {
            logger.warn("Setting job {} to finished, but it was already finished", id);
        }

        writeLogEntry(String.format("%s\t%s\t%s\t%d\n",id, LocalDateTime.now(), where, size));
    }

    public synchronized boolean isJobFinished(String id) {
        return finishedJobs.contains(id);
    }

    private void writeLogEntry(String entry) throws IOException {
        logWriter.write(entry.getBytes(StandardCharsets.UTF_8));
        logWriter.flush();
    }

    @Override
    public void close() throws Exception {
        logWriter.flush();
        logWriter.close();
    }

    public int countFinishedJobs() {
        return finishedJobs.size();
    }
}

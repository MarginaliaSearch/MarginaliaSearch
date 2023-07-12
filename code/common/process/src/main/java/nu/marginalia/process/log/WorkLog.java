package nu.marginalia.process.log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

public class WorkLog implements AutoCloseable {
    private final Set<String> finishedJobs = new HashSet<>();
    private final FileOutputStream logWriter;

    public WorkLog(Path logFile) throws IOException {
        loadLog(logFile);

        logWriter = new FileOutputStream(logFile.toFile(), true);
        writeLogEntry("# Starting WorkLog @ " + LocalDateTime.now());
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

    private void loadLog(Path logFile) throws IOException  {
        if (!Files.exists(logFile)) {
            return;
        }

        try (var lines = Files.lines(logFile)) {
            lines.filter(WorkLogEntry::isJobId)
                 .map(this::getJobIdFromWrittenString)
                 .forEach(finishedJobs::add);
        }
    }

    private static final Pattern splitPattern = Pattern.compile("\\s+");

    private String getJobIdFromWrittenString(String s) {
        return splitPattern.split(s, 2)[0];
    }

    public synchronized boolean isJobFinished(String id) {
        return finishedJobs.contains(id);
    }

    // Use synchro over concurrent set to avoid competing writes
    // - correct is better than fast here, it's sketchy enough to use
    // a PrintWriter

    public synchronized void setJobToFinished(String id, String where, int size) throws IOException {
        finishedJobs.add(id);

        writeLogEntry(String.format("%s\t%s\t%s\t%d",id, LocalDateTime.now(), where, size));
    }

    private void writeLogEntry(String entry) throws IOException {
        logWriter.write(entry.getBytes(StandardCharsets.UTF_8));
        logWriter.write("\n".getBytes(StandardCharsets.UTF_8));
        logWriter.flush();
    }

    @Override
    public void close() throws Exception {
        logWriter.flush();
        logWriter.close();
    }
}

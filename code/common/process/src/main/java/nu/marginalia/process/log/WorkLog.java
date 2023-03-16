package nu.marginalia.process.log;

import com.google.errorprone.annotations.MustBeClosed;
import org.apache.logging.log4j.util.Strings;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class WorkLog implements AutoCloseable {
    private final Set<String> finishedJobs = new HashSet<>();
    private final FileOutputStream logWriter;

    public WorkLog(Path logFile) throws IOException {
        loadLog(logFile);

        logWriter = new FileOutputStream(logFile.toFile(), true);
        writeLogEntry("# Starting WorkLog @ " + LocalDateTime.now());
    }

    public static void readLog(Path logFile, Consumer<WorkLogEntry> entryConsumer) throws FileNotFoundException {
        if (!Files.exists(logFile)) {
            throw new FileNotFoundException("Log file not found " + logFile);
        }

        try (var entries = streamLog(logFile)) {
            entries.forEach(entryConsumer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @MustBeClosed
    public static Stream<WorkLogEntry> streamLog(Path logFile) throws IOException {
        return Files.lines(logFile).filter(WorkLog::isJobId).map(line -> {
            String[] parts = line.split("\\s+");
            return new WorkLogEntry(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]));
        });
    }

    private void loadLog(Path logFile) throws IOException  {
        if (!Files.exists(logFile)) {
            return;
        }

        try (var lines = Files.lines(logFile)) {
            lines.filter(WorkLog::isJobId).map(this::getJobIdFromWrittenString).forEach(finishedJobs::add);
        }
    }

    private static boolean isJobId(String s) {
        return Strings.isNotBlank(s) && !s.startsWith("#");
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

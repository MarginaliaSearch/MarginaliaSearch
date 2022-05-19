package nu.marginalia.wmsa.edge.crawling;

import nu.marginalia.wmsa.edge.crawling.model.CrawlLogEntry;
import org.apache.logging.log4j.util.Strings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class WorkLog implements AutoCloseable {
    private final Set<String> finishedJobs = new HashSet<>();
    private final FileOutputStream logWriter;

    public WorkLog(Path logFile) throws IOException {
        loadLog(logFile);

        logWriter = new FileOutputStream(logFile.toFile(), true);
        writeLogEntry("# Starting WorkLog @ " + LocalDateTime.now());
    }

    public static void readLog(Path logFile, Consumer<CrawlLogEntry> entryConsumer) {
        if (!Files.exists(logFile)) {
            return;
        }

        try (var lines = Files.lines(logFile)) {
            lines.filter(WorkLog::isJobId).map(line -> {
                String[] parts = line.split("\\s+");
                return new CrawlLogEntry(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]));
            }).forEach(entryConsumer);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

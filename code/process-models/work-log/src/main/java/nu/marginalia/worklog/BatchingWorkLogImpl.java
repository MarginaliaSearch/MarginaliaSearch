package nu.marginalia.worklog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class BatchingWorkLogImpl implements BatchingWorkLog {
    private int batchNumber = 0;
    private final Set<String> currentBatchItems = new HashSet<>(1000);
    private final Set<String> commitedItems = new HashSet<>(10_000);
    private final OutputStream writer;

    public BatchingWorkLogImpl(Path file) throws IOException  {
        if (Files.exists(file)) {
            try (var linesStream = Files.lines(file)) {
                linesStream.map(WorkLogItem::parse).forEach(
                        item -> item.replay(this)
                );
            }

            writer = Files.newOutputStream(file, StandardOpenOption.APPEND);
            writeLogEntry(new CommentLine("Log resumed on " + LocalDateTime.now()));
            if (getCurrentBatchSize() > 0) {
                writeLogEntry(new CrashMarker());
            }
        }
        else {
            writer = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW);
            writeLogEntry(new CommentLine("Log created on " + LocalDateTime.now()));
            writeLogEntry(new CommentLine(" Format: "));
            writeLogEntry(new CommentLine(" " + AddItem.MARKER + " ID\tsignifies adding an item to the current batch"));
            writeLogEntry(new CommentLine(" " + FinishBatch.MARKER + "\tsignifies finalizing the current batch and switching to the next"));
            writeLogEntry(new CommentLine(" " + CrashMarker.MARKER + "\tdiscard contents from the current batch and start over, written after a crash"));
            writeLogEntry(new CommentLine("Upon a crash, items that have re-process until their batch is finalized"));
        }


    }

    void writeLogEntry(WorkLogItem item) throws IOException {
        item.write(this);
    }

    void writeLine(String line) throws IOException {
        writer.write(line.getBytes(StandardCharsets.UTF_8));
        writer.write('\n');
        writer.flush();
    }

    @Override
    public boolean isItemCommitted(String id) {
        return commitedItems.contains(id);
    }

    @Override
    public boolean isItemInCurrentBatch(String id) {
        return currentBatchItems.contains(id);
    }
    @Override
    public void logItem(String id) throws IOException {
        writeLogEntry(new AddItem(id));
    }

    @Override
    public void logFinishedBatch() throws IOException {
        writeLogEntry(new FinishBatch());
        incrementBatch();
    }

    void incrementBatch() {
        batchNumber++;

        // Transfer all items from the current batch to the committed items' batch
        commitedItems.addAll(currentBatchItems);
        currentBatchItems.clear();
    }

    void restartBatch() {
        currentBatchItems.clear();
    }

    void addItemToCurrentBatch(String id) {
        currentBatchItems.add(id);
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }

    @Override
    public int getBatchNumber() {
        return batchNumber;
    }

    public int getCurrentBatchSize() {
        return currentBatchItems.size();
    }
}

interface WorkLogItem {

    void replay(BatchingWorkLogImpl bwl);
    void write(BatchingWorkLogImpl bwl) throws IOException;

    static WorkLogItem parse(String line) {
        if (line.isBlank())
            return new BlankLine();

        var lineParts = LogLineParts.parse(line);

        return switch (lineParts.tag()) {
            case CommentLine.MARKER -> new CommentLine(lineParts.arg());
            case AddItem.MARKER -> new AddItem(lineParts.arg());
            case FinishBatch.MARKER -> new FinishBatch();
            case CrashMarker.MARKER -> new CrashMarker();
            default -> throw new WorkLogParseException(line);
        };
    }
}

record LogLineParts(char tag, String arg) {
    public static LogLineParts parse(String line) {
        line = line.trim();

        char tag = line.charAt(0);
        String arg = line.substring(1).trim();

        int commentIdx = arg.indexOf('#');
        if (commentIdx >= 0) arg = arg.substring(0, commentIdx).trim();

        return new LogLineParts(tag, arg);
    }
}

record CommentLine(String comment) implements WorkLogItem {
    final static char MARKER = '#';

    @Override
    public void replay(BatchingWorkLogImpl bwl) {}

    @Override
    public void write(BatchingWorkLogImpl bwl) throws IOException {
        bwl.writeLine(MARKER + " " + comment);
    }
}
record BlankLine() implements WorkLogItem {
    final static char MARKER = ' ';

    @Override
    public void replay(BatchingWorkLogImpl bwl) {}

    @Override
    public void write(BatchingWorkLogImpl bwl) throws IOException {
        bwl.writeLine(MARKER + "");
    }
}

record FinishBatch() implements WorkLogItem {
    final static char MARKER = 'F';

    @Override
    public void replay(BatchingWorkLogImpl bwl) {
        bwl.incrementBatch();
    }

    @Override
    public void write(BatchingWorkLogImpl bwl) throws IOException {
        bwl.writeLine("# " + LocalDateTime.now());
        bwl.writeLine("# finalizing batchNumber = " + bwl.getBatchNumber());
        bwl.writeLine(Character.toString(MARKER));
    }


}

record CrashMarker() implements WorkLogItem {
    final static char MARKER = 'X';

    @Override
    public void replay(BatchingWorkLogImpl bwl) {
        bwl.restartBatch();
    }

    @Override
    public void write(BatchingWorkLogImpl bwl) throws IOException {
        bwl.writeLine("# " + LocalDateTime.now());
        bwl.writeLine("# discarding batchNumber = " + bwl.getBatchNumber());
        bwl.writeLine(Character.toString(MARKER));
    }


}
record AddItem(String id) implements WorkLogItem {
    final static char MARKER = '+';

    @Override
    public void replay(BatchingWorkLogImpl bwl) {
        bwl.addItemToCurrentBatch(id);
    }

    @Override
    public void write(BatchingWorkLogImpl bwl) throws IOException {
        bwl.writeLine(MARKER + " " + id);
    }
}

class WorkLogParseException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -1238138989389021166L;

    public WorkLogParseException(String logLine) {
        super("Failed to parse work log line: '" + logLine + "'");
    }
}
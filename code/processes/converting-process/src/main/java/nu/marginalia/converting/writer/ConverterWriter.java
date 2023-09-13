package nu.marginalia.converting.writer;

import lombok.SneakyThrows;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.worklog.BatchingWorkLog;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class ConverterWriter implements AutoCloseable {

    private final BatchingWorkLog workLog;
    private final Path basePath;

    private final Duration switchInterval =
            Duration.of(10, ChronoUnit.MINUTES);
    private final ArrayBlockingQueue<ProcessedDomain> domainData =
            new ArrayBlockingQueue<>(4);

    private final Thread workerThread;

    ConverterBatchWriter writer;

    volatile boolean running = true;
    public ConverterWriter(BatchingWorkLog workLog, Path basePath) {
        this.workLog = workLog;
        this.basePath = basePath;

        workerThread = new Thread(this::writerThread, getClass().getSimpleName());
        workerThread.start();
    }

    @SneakyThrows
    public void accept(ProcessedDomain domain) {
        domainData.put(domain);
    }

    @SneakyThrows
    private void writerThread() {
        IntervalAction switcher = new IntervalAction(this::switchBatch, switchInterval);

        writer = new ConverterBatchWriter(basePath, workLog.getBatchNumber());

        while (running || !domainData.isEmpty()) {
            var data = domainData.poll(10, TimeUnit.SECONDS);

            if (data == null)
                continue;

            String id = data.id;

            if (workLog.isItemCommitted(id) || workLog.isItemInCurrentBatch(id))
                continue;

            writer.write(data);

            workLog.logItem(id);

            switcher.tick();
        }
    }

    @SneakyThrows
    public boolean switchBatch() {
        if (workLog.isCurrentBatchEmpty()) {
            // Nothing to commit
            return false;
        }

        // order matters here
        writer.close();
        workLog.logFinishedBatch();
        writer = new ConverterBatchWriter(basePath, workLog.getBatchNumber());

        return true;
    }

    @Override
    public void close() throws Exception {
        running = false;
        workerThread.join();

        // order matters here
        writer.close();
        workLog.logFinishedBatch();
    }
}

class IntervalAction {
    private final Callable<Boolean> action;
    private final Duration interval;

    private Instant nextActionInstant;

    IntervalAction(Callable<Boolean> action, Duration interval) {
        this.action = action;
        this.interval = interval;
    }

    /** Execute the provided action if enough time has passed
     * since the last successful invocation */
    public void tick() {
        if (nextActionInstant == null) {
            nextActionInstant = Instant.now().plus(interval);
            return;
        }

        if (Instant.now().isBefore(nextActionInstant))
            return;

        try {
            if (action.call()) {
                nextActionInstant = Instant.now().plus(interval);
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
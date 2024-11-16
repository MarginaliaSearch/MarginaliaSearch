package nu.marginalia.converting.writer;

import nu.marginalia.worklog.BatchingWorkLog;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class ConverterWriter implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ConverterWriter.class);

    private final BatchingWorkLog workLog;
    private final Path basePath;

    private final Duration switchInterval
            = Duration.of(10, ChronoUnit.MINUTES);
    private final ArrayBlockingQueue<ConverterBatchWritableIf> domainData
            = new ArrayBlockingQueue<>(1);

    private final Thread workerThread;

    private ConverterBatchWriter currentWriter;

    volatile boolean running = true;

    public ConverterWriter(BatchingWorkLog workLog, Path basePath) {
        this.workLog = workLog;
        this.basePath = basePath;

        workerThread = new Thread(this::writerThread, getClass().getSimpleName());
        workerThread.start();
    }

    public void accept(@Nullable ConverterBatchWritableIf domain) {
        if (null == domain)
            return;

        try {
            domainData.put(domain);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void writerThread() {
        try {
            IntervalAction switcher = new IntervalAction(this::switchBatch, switchInterval);

            currentWriter = new ConverterBatchWriter(basePath, workLog.getBatchNumber());

            while (running || !domainData.isEmpty()) {
                // poll with a timeout so we have an
                // opportunity to check the running condition
                // ... we could interrupt the thread as well, but
                // as we enter third party code it's difficult to guarantee it will deal
                // well with being interrupted
                var data = domainData.poll(1, TimeUnit.SECONDS);

                if (data == null)
                    continue;

                String id = data.id();

                if (workLog.isItemCommitted(id) || workLog.isItemInCurrentBatch(id)) {
                    logger.warn("Skipping already logged item {}", id);
                    data.close();
                    continue;
                }

                currentWriter.write(data);

                workLog.logItem(id);

                switcher.tick();
            }
        }
        catch (Exception ex) {
            logger.error("Writer thread failed", ex);
        }
    }

    public boolean switchBatch() {
        if (workLog.isCurrentBatchEmpty()) {
            // Nothing to commit
            return false;
        }


        try {
            // order matters here
            currentWriter.close();
            workLog.logFinishedBatch();
            logger.info("Switching to batch {}", workLog.getBatchNumber());
            currentWriter = new ConverterBatchWriter(basePath, workLog.getBatchNumber());

            return true;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void close() throws Exception {
        running = false;
        workerThread.join();

        // order matters here
        currentWriter.close();
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
        var now = Instant.now();
        if (nextActionInstant == null) {
            nextActionInstant = now.plus(interval);
            return;
        }

        try {
            if (now.isAfter(nextActionInstant)
                    && action.call())
            {
                nextActionInstant = now.plus(interval);
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
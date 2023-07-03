package nu.marginalia.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class AbortingScheduler {
    private final ThreadFactory threadFactory;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Nullable
    private ExecutorService executorService;

    public AbortingScheduler(String name) {
        threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(name+"client--%d")
                .setUncaughtExceptionHandler(this::handleException)
                .build();
    }

    private void handleException(Thread thread, Throwable throwable) {
        logger.error("Uncaught exception during Client IO in thread {}", thread.getName(), throwable);
    }

    public synchronized Scheduler get() {
        return Schedulers.from(getExecutorService(),
                true,
                false);
    }

    public synchronized void abort() {
        if (null != executorService) {
            executorService.shutdownNow();
            executorService = Executors.newFixedThreadPool(16, threadFactory);
        }
    }

    @Nonnull
    private synchronized ExecutorService getExecutorService() {
        if (null == executorService) {
            executorService = Executors.newFixedThreadPool(16, threadFactory);
        }
        return executorService;
    }

    public synchronized void close() {
        if (null != executorService) {
            executorService.shutdown();
        }
    }
}

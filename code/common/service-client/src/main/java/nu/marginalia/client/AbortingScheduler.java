package nu.marginalia.client;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AbortingScheduler {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Nullable
    private ExecutorService executorService;

    public AbortingScheduler() {
    }


    public synchronized Scheduler get() {
        return Schedulers.from(getExecutorService(),
                true,
                false);
    }

    public synchronized void abort() {
        if (null != executorService) {
            executorService.shutdownNow();
            executorService = Executors.newVirtualThreadPerTaskExecutor();
        }
    }

    @Nonnull
    private synchronized ExecutorService getExecutorService() {
        if (null == executorService) {
            executorService = Executors.newVirtualThreadPerTaskExecutor();
        }
        return executorService;
    }

    public synchronized void close() {
        if (null != executorService) {
            executorService.shutdown();
        }
    }
}

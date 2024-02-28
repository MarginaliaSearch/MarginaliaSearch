package nu.marginalia.service;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedExecutorFactory {

    /** Create a new fixed thread pool with the given name and number of threads. */
    public static ExecutorService createFixed(String name, int nThreads) {
        return Executors.newFixedThreadPool(nThreads, new NamedThreadFactory(name));
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String name;

        private NamedThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(@NotNull Runnable r) {
            var thread = new Thread(r, STR."\{name}[\{threadNumber.getAndIncrement()}]");
            thread.setDaemon(true);
            return thread;
        }
    }
}

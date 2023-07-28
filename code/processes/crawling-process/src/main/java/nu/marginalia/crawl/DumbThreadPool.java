package nu.marginalia.crawl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** A simple thread pool implementation that will never invoke
 * a task in the calling thread like {@link java.util.concurrent.ThreadPoolExecutor}
 * does when the queue is full. Instead, it will block until a thread
 * becomes available to run the task. This is useful for coarse grained
 * tasks where the calling thread might otherwise block for hours.
 */
public class DumbThreadPool {
    private final List<Thread> workers = new ArrayList<>();
    private final LinkedBlockingQueue<Runnable> tasks;
    private volatile boolean shutDown = false;
    private final AtomicInteger taskCount = new AtomicInteger(0);
    private final Logger logger = LoggerFactory.getLogger(DumbThreadPool.class);

    public DumbThreadPool(int poolSize, int queueSize) {
        tasks = new LinkedBlockingQueue<>(queueSize);

        for (int i = 0; i < poolSize; i++) {
            Thread worker = new Thread(this::worker, "Crawler Thread " + i);
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }

    }

    public void submit(Runnable runnable) throws InterruptedException {
        tasks.put(runnable);
    }

    public void shutDown() {
        this.shutDown = true;
    }

    public void shutDownNow()  {
        this.shutDown = true;
        for (Thread worker : workers) {
            worker.interrupt();
        }
    }

    private void worker() {
        while (!shutDown) {
            try {
                Runnable task = tasks.poll(1, TimeUnit.SECONDS);
                if (task == null) {
                    continue;
                }

                try {
                    taskCount.incrementAndGet();
                    task.run();
                }
                catch (Exception ex) {
                    logger.warn("Error executing task", ex);
                }
                finally {
                    taskCount.decrementAndGet();
                }
            }

            catch (InterruptedException ex) {
                logger.warn("Thread pool worker interrupted", ex);
                return;
            }
        }
    }


    public boolean awaitTermination(int i, TimeUnit timeUnit) {
        final long start = System.currentTimeMillis();
        final long deadline = start + timeUnit.toMillis(i);

        for (var thread : workers) {
            if (!thread.isAlive())
                continue;

            long timeRemaining = deadline - System.currentTimeMillis();

            if (timeRemaining <= 0)
                return false;

            try {
                thread.join(timeRemaining);
            }
            catch (InterruptedException ex) {
                logger.warn("Interrupted while waiting for thread pool to terminate", ex);
                return false;
            }
        }

        return true;
    }

    public int getActiveCount() {
        return taskCount.get();
    }

}

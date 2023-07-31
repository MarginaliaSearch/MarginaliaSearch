package nu.marginalia.converting;

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
// TODO: This class exists in crawler as well, should probably be broken out into a common library; use the one from crawler instead
public class DumbThreadPool {
    private final List<Thread> workers = new ArrayList<>();
    private final LinkedBlockingQueue<Runnable> tasks;
    private volatile boolean shutDown = false;
    private final AtomicInteger taskCount = new AtomicInteger(0);
    private final Logger logger = LoggerFactory.getLogger(DumbThreadPool.class);

    public DumbThreadPool(int poolSize, int queueSize) {
        tasks = new LinkedBlockingQueue<>(queueSize);

        for (int i = 0; i < poolSize; i++) {
            Thread worker = new Thread(this::worker, "Converter Thread " + i);
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


    /** Wait for all tasks to complete up to the specified timeout,
     * then return true if all tasks completed, false otherwise.
     */
    public boolean awaitTermination(int i, TimeUnit timeUnit) throws InterruptedException {
        final long start = System.currentTimeMillis();
        final long deadline = start + timeUnit.toMillis(i);

        for (var thread : workers) {
            if (!thread.isAlive())
                continue;

            long timeRemaining = deadline - System.currentTimeMillis();
            if (timeRemaining <= 0)
                return false;

            thread.join(timeRemaining);
            if (thread.isAlive())
                return false;
        }

        // Doublecheck the bookkeeping so we didn't mess up.  This may mean you have to Ctrl+C the process
        // if you see this warning forever, but for the crawler this is preferable to terminating early
        // and missing tasks.  (maybe some cosmic ray or OOM condition or X-Files baddie of the week killed a
        // thread so hard and it didn't invoke finally and didn't decrement the task count)

        int activeCount = getActiveCount();
        if (activeCount != 0) {
            logger.warn("Thread pool terminated with {} active threads(?!) -- check what's going on with jstack and kill manually", activeCount);
            return false;
        }

        return true;
    }

    public int getActiveCount() {
        return taskCount.get();
    }

}

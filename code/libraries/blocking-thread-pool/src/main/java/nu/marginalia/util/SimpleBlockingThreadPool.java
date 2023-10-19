package nu.marginalia.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** A dead simple thread pool implementation that will block the caller
 * when it is not able to perform a task.  This is desirable in batch
 * processing workloads.
 */
public class SimpleBlockingThreadPool {
    private final List<Thread> workers = new ArrayList<>();
    private final BlockingQueue<Task> tasks;
    private volatile boolean shutDown = false;
    private final AtomicInteger taskCount = new AtomicInteger(0);
    private final Logger logger = LoggerFactory.getLogger(SimpleBlockingThreadPool.class);

    public SimpleBlockingThreadPool(String name, int poolSize, int queueSize) {
        tasks = new ArrayBlockingQueue<>(queueSize);

        for (int i = 0; i < poolSize; i++) {
            Thread worker = new Thread(this::worker, name  + "[" + i + "]");
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }

    }
    public void submit(Task task) throws InterruptedException {
        tasks.put(task);
    }
    public void submitQuietly(Task task) {
        try {
            tasks.put(task);
        }
        catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
    public void shutDown() {
        this.shutDown = true;
    }

    public void shutDownNow()  {
        this.shutDown = true;
        tasks.clear();
        for (Thread worker : workers) {
            worker.interrupt();
        }
    }

    private void worker() {
        while (!(tasks.isEmpty() && shutDown)) {
            try {
                Task task = tasks.poll(1, TimeUnit.SECONDS);
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

        // Wait for termination
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

    public boolean isTerminated() {
        return shutDown && getActiveCount() == 0;
    }

    public interface Task {
        void run() throws Exception;
    }

}

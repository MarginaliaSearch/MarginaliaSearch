package nu.marginalia.ping;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;

/** In-memory schedule for updates, allowing jobs to be added and processed in order of their scheduled time.
 * This is not a particularly high-performance implementation, but exists to take contention off the database's
 * timestamp index.
 * */
public class UpdateSchedule<T, T2> {
    private final PriorityQueue<UpdateJob<T, T2>> updateQueue;
    public record UpdateJob<T, T2>(T key, Instant updateTime) {}

    public UpdateSchedule(int initialCapacity) {
        updateQueue = new PriorityQueue<>(initialCapacity, Comparator.comparing(UpdateJob::updateTime));
    }

    public synchronized void add(T key, Instant updateTime) {
        updateQueue.add(new UpdateJob<>(key, updateTime));
        notifyAll();
    }

    public synchronized T next() throws InterruptedException {
        while (true) {
            if (updateQueue.isEmpty()) {
                wait(); // Wait for a new job to be added
                continue;
            }

            UpdateJob<T, T2> job = updateQueue.peek();
            Instant now = Instant.now();

            if (job.updateTime.isAfter(now)) {
                Duration toWait = Duration.between(now, job.updateTime);
                wait(Math.max(1, toWait.toMillis()));
            }
            else {
                updateQueue.poll(); // Remove the job from the queue since it's due
                return job.key();
            }
        }
    }

    public synchronized void clear() {
        updateQueue.clear();
        notifyAll();
    }

    public synchronized void replaceQueue(Collection<UpdateJob<T,T2>> newJobs) {
        updateQueue.clear();
        updateQueue.addAll(newJobs);
        notifyAll();
    }
}

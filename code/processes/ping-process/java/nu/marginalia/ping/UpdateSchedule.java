package nu.marginalia.ping;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

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

    /** Returns the next job in the queue that is due to be processed.
     * If no jobs are due, it will block until a job is added or a job becomes due.
     * */
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


    /** Returns the first job in the queue matching the predicate that is not scheduled into the future,
     * blocking until a job is added or a job becomes due.
     */
    public synchronized T nextIf(Predicate<T> predicate) throws InterruptedException {
        List<UpdateJob<T, T2>> rejectedJobs = new ArrayList<>();

        try {
            while (true) {
                if (updateQueue.isEmpty()) {
                    wait(); // Wait for a new job to be added
                    continue;
                }

                UpdateJob<T, T2> job = updateQueue.peek();
                Instant now = Instant.now();

                if (job.updateTime.isAfter(now)) {
                    Duration toWait = Duration.between(now, job.updateTime);

                    // Return the rejected jobs to the queue for other threads to process
                    updateQueue.addAll(rejectedJobs);
                    if (!rejectedJobs.isEmpty())
                        notifyAll();
                    rejectedJobs.clear();

                    wait(Math.max(1, toWait.toMillis()));
                } else {
                    var candidate = updateQueue.poll(); // Remove the job from the queue since it's due

                    assert candidate != null : "Update job should not be null at this point, since we just peeked it in a synchronized block";

                    if (!predicate.test(candidate.key())) {
                        rejectedJobs.add(candidate);
                    }
                    else {
                        return candidate.key();
                    }
                }
            }
        }
        finally {
            // Return the rejected jobs to the queue for other threads to process
            updateQueue.addAll(rejectedJobs);
            if (!rejectedJobs.isEmpty())
                notifyAll();
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

package nu.marginalia.piping;

import java.time.Duration;
import java.util.Optional;

public interface PipeStage<T> {

    /** Janitor's shutdown of the pipe, remaining data will be left unprocessed */
    void stop();

    /** Signal to the pipe that we will not be providing any more inputs.
     * This will initiate a graceful eventual shutdown that will
     * cascade through the entire pipe as the stages drain of work items,
     * as runners will terminate if they find no input and sees this state */
    void stopFeeding();

    /** Awaits shutdown */
    void join() throws InterruptedException;

    /** Awaits shutdown for millis, returns true if the stage was shut down before the timeout.
     * */
    boolean join(long millis) throws InterruptedException;

    /** Returns true if all threads are idle and the input queue is also empty */
    boolean isQuiet();

    Optional<PipeStage<?>> next();

    boolean offer(T val);
    boolean offer(T val, Duration timeout);

    int getInstanceCount();

    /** Set the desired instance count.  If this is higher than the actual instance count,
     * a new thread will be started.  If this is lower than the actual instance count,
     * threads will shut down to match the request the next time they drain the input queue.
     */
    void setDesiredInstanceCount(int newDic);
}

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

    Optional<PipeStage<?>> next();

    boolean offer(T val);
    boolean offer(T val, Duration timeout);
}

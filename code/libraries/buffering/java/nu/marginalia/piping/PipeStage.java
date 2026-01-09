package nu.marginalia.piping;

import java.time.Duration;
import java.util.Optional;

public interface PipeStage<T> {
    void stop();
    void stopFeeding();
    void join() throws InterruptedException;
    boolean join(long millis) throws InterruptedException;

    Optional<PipeStage<?>> next();

    boolean offer(T val);
    boolean offer(T val, Duration timeout);

    int getInstanceCount();

    void setDesiredInstanceCount(int newDic);
}

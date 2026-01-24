package nu.marginalia.piping;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class BufferPipe<T> implements AutoCloseable {
    private final List<PipeStage<?>> stages;
    private final PipeStage<T> firstStage;

    public interface IntermediateFunction<IN, OUT> {
        void process(IN input, PipeDrain<OUT> output) throws Exception;
        default void cleanUp() {}
    }
    public interface FinalFunction<IN> {
        void process(IN input) throws Exception;
        default void cleanUp() {}
    }

    public static <S> BufferPipeBuilder<S, S> builder(ExecutorService executorService) {
        return new BufferPipeBuilder<>(executorService);
    }

    BufferPipe(PipeStage<T> firstStage) {
        this.stages = new ArrayList<>();
        this.firstStage = firstStage;
        stages.add(firstStage);

        for (;;) {
            var last = stages.getLast();
            var next = last.next();
            if (next.isPresent()) {
                stages.add(next.get());
            }
            else break;
        }
    }

    /** Provide input to the pipe, blocking if necessary */
    public void offer(T val) {
        firstStage.offer(val);
    }

    /** Provide input to the pipe, blocking for up to 'duration' if necessary.
     * Returns true if successful.
     */
    public boolean offer(T val, Duration timeout) {
        if (timeout.isNegative())
            return false;

        return firstStage.offer(val, timeout);
    }

    /** Initiate a graceful shutdown of the pipe, which will terminate after all
     * inputs are completely processed.  Returns immediately.  Use join() to await shutdown. */
    public void stopFeeding() {
        firstStage.stopFeeding();
    }

    /** Stop the pipe ASAP, remaining data will be unprocessed */
    public void stop() {

        for (var stage : stages) {
            stage.stop();
        }
        try {
            for (var stage : stages) {
                stage.join();
            }
        }
        catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    /** Wait for the stages to shut down after stopFeeding() has been invoked  */
    public void join() throws InterruptedException {
        for (var stage : stages) {
            stage.join();
        }
    }

    /** Wait for the stages to shut down after stopFeeding() has been invoked,
     * for up to 'millis' ms.  Returns true if the pipe is shut down.
     * */
    public boolean join(long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;

        for (var stage : stages) {
            if (!stage.join(end - System.currentTimeMillis()))
                return false;
        }
        return true;
    }


    @Override
    public void close() {
        stop();
    }

}

package nu.marginalia.piping;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class BufferPipe<T> {
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

    public BufferPipe(PipeStage<T> firstStage) {
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

    public void offer(T val) {
        firstStage.offer(val);
    }

    public boolean offer(T val, Duration timeout) {
        if (timeout.isNegative())
            return false;

        return firstStage.offer(val, timeout);
    }

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


    public void stopFeeding() {
        firstStage.stopFeeding();
    }

    public void join() throws InterruptedException {
        for (var stage : stages) {
            stage.join();
        }
    }

    public boolean join(long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;

        for (var stage : stages) {
            if (!stage.join(end - System.currentTimeMillis()))
                return false;
        }
        return true;
    }
}

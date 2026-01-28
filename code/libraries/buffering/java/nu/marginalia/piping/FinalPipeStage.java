package nu.marginalia.piping;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class FinalPipeStage<T> extends AbstractPipeStage<T> {

    private final Supplier<BufferPipe.FinalFunction<T>> constructor;

    public FinalPipeStage(String name,
                          int size,
                          int concurrency,
                          Duration maxRunDuration,
                          Supplier<BufferPipe.FinalFunction<T>> constructor,
                          ExecutorService executorService)
    {
        super(name, size, concurrency, maxRunDuration, executorService);
        this.constructor = constructor;
    }


    public Optional<PipeStage<?>> next() {
        return Optional.empty();
    }

    @Override
    StageExecution<T> createStage() {
        return new StageExecution<T>() {
            final BufferPipe.FinalFunction<T> ff = constructor.get();

            @Override
            public void accept(T val) {
                try {
                    ff.process(val);
                }
                catch (Exception ex) {
                    logger.error("Error in processing stage {}", stageName, ex);
                }
            }

            @Override
            public void cleanUp() {
                ff.cleanUp();
            }
        };
    }


}

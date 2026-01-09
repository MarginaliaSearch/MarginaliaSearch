package nu.marginalia.piping;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class IntermediatePipeStage<T, T2> extends AbstractPipeStage<T> {

    private final Supplier<BufferPipe.IntermediateFunction<T, T2>> constructor;
    private final PipeStage<T2> next;

    public IntermediatePipeStage(String stageName,
                                 int size,
                                 int concurrency,
                                 Supplier<BufferPipe.IntermediateFunction<T, T2>> constructor,
                                 ExecutorService executorService,
                                 PipeStage<T2> next) {
        super(stageName, size, executorService);
        this.constructor = constructor;
        this.next = next;
        setDesiredInstanceCount(concurrency);
    }

    public Optional<PipeStage<?>> next() {
        return Optional.of(next);
    }

    @Override
    StageExecution<T> createStage() {
        return new StageExecution<>() {
            final BufferPipe.IntermediateFunction<T,T2> ff = constructor.get();

            @Override
            public void accept(T val) {
                try {
                    ff.process(val, next::offer);
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

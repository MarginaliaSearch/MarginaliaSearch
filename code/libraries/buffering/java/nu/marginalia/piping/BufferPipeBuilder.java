package nu.marginalia.piping;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public class BufferPipeBuilder<START, T> {
    private final List<IntermediateStage> stageList = new ArrayList<>();
    private final ExecutorService executorService;

    public BufferPipeBuilder(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public static <S> BufferPipeBuilder<S, S> of(ExecutorService executorService) {
        return new BufferPipeBuilder<S, S>(executorService);
    }

    record IntermediateStage(String name, int size, int concurrency, Supplier cons) {}

    public <T2> BufferPipeBuilder<START, T2> addStage(String name, int size, int concurrency, Supplier<BufferPipe.IntermediateFunction<T, T2>> cons) {
        stageList.add(new IntermediateStage(
                name,
                size,
                concurrency,
                cons
        ));

        return (BufferPipeBuilder<START, T2>) this;
    }

    @SuppressWarnings("unchecked")
    public BufferPipe<START> finalStage(String name, int size, int concurrency, Supplier<BufferPipe.FinalFunction<T>> cons) {
        PipeStage tail = new FinalPipeStage(name, size, concurrency, cons, executorService);
        while (!stageList.isEmpty()) {
            var stage = stageList.removeLast();
            tail = new IntermediatePipeStage(
                    stage.name,
                    stage.size,
                    stage.concurrency,
                    stage.cons,
                    executorService,
                    tail
            );
        }

        return new BufferPipe<START>(tail);
    }

}

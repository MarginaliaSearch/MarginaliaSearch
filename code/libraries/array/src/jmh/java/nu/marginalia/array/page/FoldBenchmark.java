package nu.marginalia.array.page;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.SimulatedNaiveArray;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;

public class FoldBenchmark {

    @State(Scope.Benchmark)
    public static class BenchState {

        @Setup(Level.Trial)
        public void doSetup() {
            array.transformEach(0, size, (pos,old) -> ~pos);
            array2.transformEach(0, size, (pos,old) -> ~pos);
            array3.transformEach(0, size, (pos,old) -> ~pos);
            simulateNaiveApproach.transformEach(0, size, (pos,old) -> ~pos);
        }

        int size = 100*1024*1024;
        int pageSize = 10*1024;
        LongArray array = LongArray.allocate(size);
        LongArray array2 = SegmentLongArray.onHeap(Arena.ofShared(), size);
        LongArray array3 = SegmentLongArray.onHeap(Arena.ofConfined(), size);
        LongArray simulateNaiveApproach = new SimulatedNaiveArray(size, pageSize);
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchArrayFoldGoldStandard(BenchState state) {
        return state.array.fold(0, 0, state.size, Long::sum);
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchArrayFoldGoldStandard2(BenchState state) {
        return state.array2.fold(0, 0, state.size, Long::sum);
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchArrayFoldGoldStandard3(BenchState state) {
        return state.array3.fold(0, 0, state.size, Long::sum);
    }



    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchArrayFoldNaive(BenchState state) {
        return state.simulateNaiveApproach.fold(0, 0, state.size, Long::sum);
    }

}

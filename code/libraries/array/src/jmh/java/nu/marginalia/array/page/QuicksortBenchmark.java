package nu.marginalia.array.page;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import org.openjdk.jmh.annotations.*;

/** This benchmark simulates the sorting in index creation */
public class QuicksortBenchmark {

    @State(Scope.Benchmark)
    public static class BenchState {

        @Setup(Level.Invocation)
        public void doSetup() {
            array.transformEach(0, size, (pos,old) -> ~pos);
        }

        int size = 1024*1024;
        int pageSize = 10*1024;
        LongArray array = LongArrayFactory.onHeapShared(size);
    }

    @Fork(value = 2, warmups = 5)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray javaSort(BenchState state) {
        var array = state.array;

        array.quickSortN(2, 0, array.size());

        return array;
    }

    @Fork(value = 2, warmups = 5)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray cppSort(BenchState state) {

        var array = state.array;

        array.quickSortNative128(0, array.size());

        return array;
    }

}

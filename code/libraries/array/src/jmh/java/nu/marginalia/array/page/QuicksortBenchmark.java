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

    @Fork(value = 5, warmups = 1)
    @Warmup(iterations = 5)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray javaSort(BenchState state) {
        var array = state.array;

        array.quickSortJava(0, array.size());

        return array;
    }

    @Fork(value = 5, warmups = 1)
    @Warmup(iterations = 5)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray cppSort(BenchState state) {

        var array = state.array;

        array.quickSortNative(0, array.size());

        return array;
    }

}

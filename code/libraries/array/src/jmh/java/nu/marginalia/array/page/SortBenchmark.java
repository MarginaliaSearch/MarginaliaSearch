package nu.marginalia.array.page;

import nu.marginalia.array.LongArray;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;

/** This benchmark simulates the sorting in index creation */
public class SortBenchmark {

    @State(Scope.Benchmark)
    public static class BenchState {

        @Setup(Level.Invocation)
        public void doSetup() {
            msArray.transformEach(0, size, (pos,old) -> ~pos);
            usArray.transformEach(0, size, (pos,old) -> ~pos);
        }

        int size = 1024*1024;

        LongArray msArray = SegmentLongArray.onHeap(Arena.ofConfined(), size);
        LongArray usArray = UnsafeLongArray.onHeap(Arena.ofConfined(), size);
    }

    @Fork(value = 5, warmups = 5)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray msSort64(BenchState state) {
        var array = state.msArray;

        array.quickSortJavaN(2, 0, array.size());

        return array;
    }

    @Fork(value = 5, warmups = 5)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray msSort128(BenchState state) {
        var array = state.msArray;

        array.quickSortJavaN(2, 0, array.size());

        return array;
    }

    @Fork(value = 5, warmups = 5)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray usSort128(BenchState state) {
        var array = state.usArray;

        array.quickSortJavaN(2, 0, array.size());

        return array;
    }

    @Fork(value = 5, warmups = 5)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray usSort64(BenchState state) {
        var array = state.usArray;

        array.quickSortJavaN(2, 0, array.size());

        return array;
    }

    // We can assign the C++ sorts to lower warmup values as the JIT does not
    // need to warm up the C++ code; only the small Java code that calls it.

    @Fork(value = 5, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray cppSort128(BenchState state) {

        var array = state.usArray; // realistically doesn't matter

        array.quickSortNative128(0, array.size());

        return array;
    }

    @Fork(value = 5, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray cppSort64(BenchState state) {

        var array = state.usArray; // realistically doesn't matter

        array.quickSortNative(0, array.size());

        return array;
    }
}

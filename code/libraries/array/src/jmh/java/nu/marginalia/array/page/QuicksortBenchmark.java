package nu.marginalia.array.page;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.SimulatedNaiveArray;
import nu.marginalia.array.scheme.ArrayPartitioningScheme;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;

/** This benchmark simulates the sorting in index creation */
public class QuicksortBenchmark {

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
    public LongArray benchArrayFoldGoldStandard(BenchState state) {
        var array = state.array;

        for (int i = 0; i + 100 < state.size; i+=100) {
            array.quickSort(i, i + 100);
        }

        return array;
    }
    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray benchArrayFoldGoldStandard2(BenchState state) {
        var array = state.array2;

        for (int i = 0; i + 100 < state.size; i+=100) {
            array.quickSort(i, i + 100);
        }

        return array;
    }
    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray benchArrayFoldGoldStandard3(BenchState state) {
        var array = state.array3;

        for (int i = 0; i + 100 < state.size; i+=100) {
            array.quickSort(i, i + 100);
        }

        return array;
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray benchArrayFoldNaive(BenchState state) {
        var array = state.simulateNaiveApproach;

        for (int i = 0; i + 100 < state.size; i+=100) {
            array.quickSort(i, i + 100);
        }

        return array;
    }

}

package nu.marginalia.array.page;

import nu.marginalia.array.LongArray;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.util.Random;

/** This benchmark simulates the searching in index querying */
public class SearchBenchmark {

    @State(Scope.Benchmark)
    public static class SortState {

        public SortState()
        {
            msArray.transformEach(0, size, (pos,old) -> ~pos);
            usArray.transformEach(0, size, (pos,old) -> ~pos);
            msArray.quickSortJava(0, size);
            usArray.quickSortJava(0, size);
            keys = new long[1000];
            Random r = new Random();
            for (int i = 0; i < 1000; i++) {
                keys[i] = msArray.get(r.nextInt(0, size));
            }
        }

        int size = 1024*1024;

        long[] keys;
        LongArray msArray = SegmentLongArray.onHeap(Arena.ofConfined(), size);
        LongArray usArray = UnsafeLongArray.onHeap(Arena.ofConfined(), size);
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 5)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long msSort64(SortState state) {
        var array = state.usArray;

        long ret = 0;
        for (var key : state.keys) {
            ret += array.binarySearchNJava(2, key, 0, array.size());
        }

        return ret;
    }

    @Fork(value = 3, warmups = 5)
    @Warmup(iterations = 5)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long msSort64_2(SortState state) {
        var array = state.usArray;

        long ret = 0;
        for (var key : state.keys) {
            ret += array.binarySearchNJava2(2, key, 0, array.size());
        }

        return ret;
    }

    @Fork(value = 5, warmups = 5)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long msSort128(SortState state) {
        var array = state.msArray;

        long ret = 0;
        for (var key : state.keys) {
            ret += array.binarySearchNJava(2, 0, array.size(), key);
        }

        return ret;
    }

    @Fork(value = 5, warmups = 5)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long usSort64(SortState state) {
        var array = state.usArray;

        long ret = 0;
        for (var key : state.keys) {
            ret += array.binarySearchUpperBoundJava(0, array.size(), key);
        }

        return ret;
    }

    @Fork(value = 5, warmups = 5)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long usSort128(SortState state) {
        var array = state.usArray;

        long ret = 0;
        for (var key : state.keys) {
            ret += array.binarySearchNJava(2, 0, array.size(), key);
        }

        return ret;
    }

}

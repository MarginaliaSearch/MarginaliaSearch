package nu.marginalia.array.page;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.SimulatedNaiveArray;
import nu.marginalia.array.scheme.ArrayPartitioningScheme;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;

/** This benchmark simulates the sorting in index creation */
public class QuicksortBenchmark {

    @State(Scope.Benchmark)
    public static class BenchState {

        @Setup(Level.Trial)
        public void doSetup() {
            array.transformEach(0, size, (pos,old) -> ~pos);
            pagingArray.transformEach(0, size, (pos,old) -> ~pos);
            simulateNaiveApproach.transformEach(0, size, (pos,old) -> ~pos);
        }

        int size = 100*1024*1024;
        int pageSize = 10*1024;
        LongArray array = LongArray.allocate(size);
        LongArray pagingArray;
        LongArray simulateNaiveApproach = new SimulatedNaiveArray(size, pageSize);

        {
            // Artificially create an unnaturally small PagingLongArray to compare with SimulatedNaiveArray above
            LongArrayPage[] pages = new LongArrayPage[size / pageSize];
            for (int i = 0; i < pages.length; i++) {
                pages[i] = new LongArrayPage(ByteBuffer.allocateDirect(8*pageSize));
            }
            pagingArray = new PagingLongArray(ArrayPartitioningScheme.forPartitionSize(pageSize), pages, size);
        }
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
    public LongArray benchArrayFoldNaive(BenchState state) {
        var array = state.simulateNaiveApproach;

        for (int i = 0; i + 100 < state.size; i+=100) {
            array.quickSort(i, i + 100);
        }

        return array;
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public LongArray benchArrayPaged(BenchState state) {
        var array = state.pagingArray;

        for (int i = 0; i + 100 < state.size; i+=100) {
            array.quickSort(i, i + 100);
        }

        return array;
    }

}

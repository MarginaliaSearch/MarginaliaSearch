package nu.marginalia.array.page;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.SimulatedNaiveArray;
import nu.marginalia.array.scheme.ArrayPartitioningScheme;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;

public class FoldBenchmark {

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
    public long benchArrayFoldGoldStandard(BenchState state) {
        return state.array.fold(0, 0, state.size, Long::sum);
    }


    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchArrayFoldNaive(BenchState state) {
        return state.simulateNaiveApproach.fold(0, 0, state.size, Long::sum);
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchArrayPaged(BenchState state) {
        return state.pagingArray.fold(0, 0, state.size, Long::sum);
    }

}

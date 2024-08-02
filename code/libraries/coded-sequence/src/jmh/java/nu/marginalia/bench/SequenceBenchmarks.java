package nu.marginalia.bench;

import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.sequence.VarintCodedSequence;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;

public class SequenceBenchmarks {

    @State(Scope.Benchmark)
    public static class SequenceState {
        VarintCodedSequence vcs;
        GammaCodedSequence gcs;
        ByteBuffer workArea;
        int[] valueBuffer;
        public SequenceState()
        {
            valueBuffer = new int[128];

            workArea = ByteBuffer.allocate(65536);
            vcs = VarintCodedSequence.generate(1, 3, 5, 16, 1024, 2048);
            gcs = GammaCodedSequence.generate(workArea, 1, 3, 5, 16, 1024, 2048);
        }
    }

    @Fork(value = 5, warmups = 5)
    @Warmup(iterations = 5)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public int vcsDecode(SequenceState state) {
        var iter = state.vcs.iterator();
        int sum = 0;
        while (iter.hasNext()) {
            sum += iter.nextInt();
        }
        return sum;
    }


//    @Fork(value = 1, warmups = 1)
//    @Warmup(iterations = 1)
//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    public int gcsDecode(SequenceState state) {
//        var iter = state.gcs.iterator();
//        int sum = 0;
//        while (iter.hasNext()) {
//            sum += iter.nextInt();
//        }
//        return sum;
//    }

//    @Fork(value = 1, warmups = 1)
//    @Warmup(iterations = 1)
//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    public VarintCodedSequence vcsEncode(SequenceState state) {
//        return VarintCodedSequence.generate(1, 3, 5, 16, 1024, 2048, 4096, 4098, 4100);
//    }

//    @Fork(value = 1, warmups = 1)
//    @Warmup(iterations = 1)
//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    public GammaCodedSequence gcsEncode(SequenceState state) {
//        return GammaCodedSequence.generate(state.workArea, 1, 3, 5, 16, 1024, 2048, 4096, 4098, 4100);
//    }


}

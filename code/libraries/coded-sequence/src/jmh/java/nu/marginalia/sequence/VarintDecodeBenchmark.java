package nu.marginalia.sequence;

import it.unimi.dsi.fastutil.ints.IntList;
import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/** Measures the varint bulk decode over the delta widths typical of position data.
 *  A wide load variant, getLong plus a leading zero scan over the continuation
 *  bits, has been evaluated here and lost to the byte loop by 1.4-2.4x.  The
 *  single byte fast path is near optimal for position shaped data. */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class VarintDecodeBenchmark {

    /** Maximum delta between consecutive values.  100 encodes mostly as one byte,
     *  10000 as two, the widest as three to four. */
    @Param({"100", "10000", "10000000"})
    int maxDelta;

    @Param({"8", "64"})
    int n;

    VarintCodedSequence sequence;

    @Setup
    public void setup() {
        Random r = new Random(1);
        int[] values = new int[n];
        int val = 0;
        for (int i = 0; i < n; i++) {
            val += 1 + r.nextInt(maxDelta);
            values[i] = val;
        }
        sequence = VarintCodedSequence.generate(values);
    }

    @Benchmark
    public IntList decode() {
        return sequence.values();
    }
}

package bs_vs_ls;

import org.openjdk.jmh.annotations.*;

import java.util.Arrays;
import java.util.stream.LongStream;

public class BinSearchVsLinSearch {
    static final long[] data = LongStream.generate(() -> (long) (Long.MAX_VALUE * Math.random())).limit(512).sorted().toArray();

    @State(Scope.Thread)
    public static class Target {
        long targetValue = 0;

        @Setup(Level.Invocation)
        public void setUp() {
             targetValue = data[(int)(data.length * Math.random())];
        }

    }

//    @Benchmark
    public long testBs(Target t) {
        return Arrays.binarySearch(data, t.targetValue);
    }

//    @Benchmark
    public long testLs(Target t) {
        for (int i = 0; i < 512; i++) {
            if (data[i] > t.targetValue)
                break;
            else if (data[i] == t.targetValue)
                return i;
        }
        return -1;
    }
}

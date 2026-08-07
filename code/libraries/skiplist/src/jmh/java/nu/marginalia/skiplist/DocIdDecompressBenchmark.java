package nu.marginalia.skiplist;

import nu.marginalia.ffi.NativeAlgos;
import nu.marginalia.skiplist.compression.DocIdCompressor;
import nu.marginalia.skiplist.compression.input.ArrayCompressorInput;
import nu.marginalia.skiplist.compression.output.ByteBufferCompressorBuffer;
import nu.marginalia.skiplist.compression.output.SegmentCompressorBuffer;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class DocIdDecompressBenchmark {

    @Param({"256", "1048576", "1099511627776"})
    long maxDelta;

    @Param({"16", "1000"})
    int n;

    MemorySegment data;
    long[] output;

    @Setup
    public void setup() {
        Arena arena = Arena.ofShared();
        MemorySegment seg = arena.allocate(n * 9L + 64, 8);

        output = new long[n];

        Random r = new Random(1);
        long[] input = new long[n];
        input[0] = r.nextLong(1, maxDelta);
        for (int i = 1; i < n; i++) {
            input[i] = input[i-1] + r.nextLong(1, maxDelta);
        }

        DocIdCompressor.compress(new ArrayCompressorInput(input), n, new ByteBufferCompressorBuffer(seg.asByteBuffer()));
        data = seg;
    }

    @Benchmark
    public long javaDecompress() {
        DocIdCompressor.decompressJava(new SegmentCompressorBuffer(data, 0), n, output);
        return output[n - 1];
    }

    @Benchmark
    public long nativeDecompress() {
        NativeAlgos.decompressDocIds(data, 0, n, output);
        return output[n - 1];
    }
}

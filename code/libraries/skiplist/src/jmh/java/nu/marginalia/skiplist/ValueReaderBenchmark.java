package nu.marginalia.skiplist;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.pool.BufferPool;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static nu.marginalia.skiplist.SkipListConstants.RECORD_SIZE;

/** Exercises the ValueReader offsets stream, which is dominated by
 *  readOffsetsForBlock_Compressed matching input keys against decompressed blocks */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class ValueReaderBenchmark {

    static final int LIST_SIZE = 200_000;

    /** Input keys select every stride'th entry of the posting list.  A large stride
     *  means sparse keys that scan past many non matching records. */
    @Param({"1", "16", "256"})
    int stride;

    Path docsFile;
    Path valuesFile;

    BufferPool pool;
    SkipListValueReader valueReader;
    long[] inputKeys;

    MemorySegment scratch;
    SegmentAllocator scratchAllocator;

    @Setup
    public void setup() throws IOException {
        docsFile = Files.createTempFile("ValueReaderBenchmark", ".docs.dat");
        valuesFile = Files.createTempFile("ValueReaderBenchmark", ".values.dat");

        Arena arena = Arena.ofShared();
        MemorySegment ms = arena.allocate((long) LIST_SIZE * RECORD_SIZE * 8);
        for (int i = 0; i < LIST_SIZE; i++) {
            ms.setAtIndex(ValueLayout.JAVA_LONG, (long) RECORD_SIZE * i, 2L * i + 1);
            for (int vi = 1; vi < RECORD_SIZE; vi++) {
                ms.setAtIndex(ValueLayout.JAVA_LONG, (long) RECORD_SIZE * i + vi, -i);
            }
        }
        LongArray array = LongArrayFactory.wrap(ms);

        try (var writer = new SkipListWriter(docsFile, valuesFile)) {
            writer.writeList(array, LIST_SIZE);
        }

        pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 64);
        valueReader = new SkipListValueReader(valuesFile);

        inputKeys = new long[LIST_SIZE / stride];
        for (int i = 0; i < inputKeys.length; i++) {
            inputKeys[i] = 2L * (i * stride) + 1;
        }

        scratch = arena.allocate(1L << 22, 16);
        scratchAllocator = (byteSize, byteAlignment) -> scratch.asSlice(0, byteSize);
    }

    @TearDown
    public void tearDown() throws Exception {
        pool.close();
        valueReader.close();
        Files.deleteIfExists(docsFile);
        Files.deleteIfExists(valuesFile);
    }

    @Benchmark
    public long streamOffsets() throws IOException {
        var reader = new SkipListReader(pool, valueReader, 0);
        var vr = reader.getValueReader(scratchAllocator, inputKeys);

        long sum = 0;
        while (vr.advance()) {
            sum += vr.getValue(0);
        }
        return sum;
    }
}

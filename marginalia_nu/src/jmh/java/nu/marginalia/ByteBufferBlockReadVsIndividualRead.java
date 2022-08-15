package nu.marginalia;

import lombok.SneakyThrows;
import nu.marginalia.util.multimap.MultimapFileLong;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class ByteBufferBlockReadVsIndividualRead {

    @State(Scope.Benchmark)
    public static class ByteBufferState {
        private MultimapFileLong mmf;
        private Path file;
        private static final int size = 800*1024*1024;
        @Setup(Level.Iteration)
        @SneakyThrows
        public void setUp() {
            file = Files.createTempFile("jmh", ".dat");
            mmf = MultimapFileLong.forOutput(file, size);
            for (int i = 0; i < size; i++) {
                mmf.put(i, i);
            }
        }

        @TearDown(Level.Iteration)
        @SneakyThrows
        public void tearDown() {
            mmf.close();
            Files.delete(file);
        }

        LongStream basicStream() {
            return IntStream.range(0, size).mapToLong(mmf::get);
        }

        LongStream blockStream(int blockSize) {
            long urlOffset = 0;
            long endOffset = size;

            long[] arry = new long[blockSize];

            return LongStream
                    .iterate(urlOffset, i -> i< endOffset, i->i+blockSize)
                    .flatMap(pos -> {
                        int sz = (int)(Math.min(pos+blockSize, endOffset) - pos);
                        mmf.read(arry, sz, pos);
                        return Arrays.stream(arry, 0, sz);
                    });
        }
    }



    // @Benchmark @BenchmarkMode(Mode.Throughput)
    // @Fork(value = 1, warmups = 1)
    // @Warmup(iterations = 1)
    public long testBasic(ByteBufferState state) {
        return state.basicStream().sum();
    }


    @Benchmark @BenchmarkMode(Mode.Throughput)
    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 0)
    public long testBlock128(ByteBufferState state) {
        return state.blockStream(128).sum();
    }
    @Benchmark @BenchmarkMode(Mode.Throughput)
    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 0)
    public long testBlock1024(ByteBufferState state) {
        return state.blockStream(1024).sum();
    }
    @Benchmark @BenchmarkMode(Mode.Throughput)
    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 0)
    public long testBlock8192(ByteBufferState state) {
        return state.blockStream(8192).sum();
    }
}

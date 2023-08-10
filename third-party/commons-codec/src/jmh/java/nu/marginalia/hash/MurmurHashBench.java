package nu.marginalia.hash;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.commons.codec.digest.MurmurHash3;
import org.openjdk.jmh.annotations.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MurmurHashBench {

    private static HashFunction guavaHashFunction = Hashing.murmur3_128();
    private static MurmurHash3_128 marginaliahash = new MurmurHash3_128();

    @State(Scope.Benchmark)
    public static class BenchState {

        List<String> strings;

        @Setup(Level.Trial)
        public void doSetup() {
            strings = new ArrayList<>();
            try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("dictionary/en-1000"),
                    "Could not load word frequency table");
                 var br = new BufferedReader(new InputStreamReader(resource))
            ) {
                for (;;) {
                    String s = br.readLine();
                    if (s == null) {
                        break;
                    }
                    strings.add(s.toLowerCase());
                }
            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchGuava(BenchState state) {
        long total = 0;
        for (var string : state.strings) {
            total += guavaHashFunction.hashUnencodedChars(string).padToLong();
        }
        return total;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchCommonCodec(BenchState state) {
        long total = 0;
        for (var string : state.strings) {
            total += MurmurHash3.hash128x64(string.getBytes(StandardCharsets.UTF_8))[0];
        }
        return total;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchMarginalia_hashNonStandardASCIIOnlyDirect(BenchState state) {
        long total = 0;
        for (var string : state.strings) {
            total += marginaliahash.hashLowerBytes(string);
        }
        return total;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchMarginalia_hashStandard(BenchState state) {
        long total = 0;
        for (var string : state.strings) {
            total += marginaliahash.hash(string.getBytes(StandardCharsets.UTF_8));
        }
        return total;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchJavaStringHash(BenchState state) {
        long total = 0;
        for (var string : state.strings) {
            total += string.hashCode();
        }
        return total;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long benchWeakNonAscii(BenchState state) {
        long total = 0;
        for (var string : state.strings) {
            total += marginaliahash.hashNearlyASCII(string);
        }
        return total;
    }
}

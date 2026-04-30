package ca.rmen.porterstemmer;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class StemmerBench {

    public String wordlist = "/usr/share/dict/words";

    private List<String> words;
    private final PorterStemmer current = new PorterStemmer();
    private final PorterStemmerOriginal original = new PorterStemmerOriginal();

    @Setup(Level.Trial)
    public void loadWords() throws IOException {
        words = Files.readAllLines(Path.of(wordlist));
    }

    @Benchmark
    public int stemCurrent() {
        // Sum the stem lengths so the JIT can't dead-code the call.
        int acc = 0;
        for (int i = 0, n = words.size(); i < n; i++) {
            acc += current.stemWord(words.get(i)).length();
        }
        return acc;
    }

    @Benchmark
    public int stemOriginal() {
        int acc = 0;
        for (int i = 0, n = words.size(); i < n; i++) {
            acc += original.stemWord(words.get(i)).length();
        }
        return acc;
    }
}

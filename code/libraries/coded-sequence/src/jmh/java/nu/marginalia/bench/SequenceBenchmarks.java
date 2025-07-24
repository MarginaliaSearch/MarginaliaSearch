package nu.marginalia.bench;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.sequence.SequenceOperations;
import org.openjdk.jmh.annotations.*;

import java.util.Random;

public class SequenceBenchmarks {

    @State(Scope.Benchmark)
    public static class SequenceState {
        IntList a;
        IntList b;
        IntList c;


        public SequenceState() {
            a = new IntArrayList();
            b = new IntArrayList();
            c = new IntArrayList();

            var r = new Random(1000);

            for (int i = 0; i < 100; i++) {
                b.add(r.nextInt(0, 500));
                c.add(r.nextInt(0, 500));
            }

            for (int i = 0; i < 1000; i++) {
                a.add(r.nextInt(0, 5000));
            }
        }
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public IntList intersect(SequenceState state) {
        return SequenceOperations.findIntersections(state.a, state.b, state.c);
    }



}

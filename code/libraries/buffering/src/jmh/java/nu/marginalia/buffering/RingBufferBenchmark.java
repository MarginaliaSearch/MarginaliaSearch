package nu.marginalia.buffering;

import org.openjdk.jmh.annotations.*;

public class RingBufferBenchmark {

    @State(Scope.Benchmark)
    public static class BenchState {

        RingBuffer<Object> ringBuffer = new RingBuffer<>(16);
        SingBuffer<Object> singBuffer = new SingBuffer<>();
        SingBuffer<Object> singBuffer2 = new SingBuffer<>();
        Object o = new Object();

        @Setup(Level.Invocation)
        public void doSetup() {
            ringBuffer.reset();
            ringBuffer.put(o);
            singBuffer.reset();
            singBuffer2.reset();
            singBuffer2.put(o);
        }

    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public Object singPut(BenchState state) {
        state.singBuffer.put(state);
        return state;
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public Object singTake(BenchState state) {
        return state.singBuffer2.take();
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public Object put1P(BenchState state) {
        state.ringBuffer.put(state);
        return state.ringBuffer;
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public Object putNP(BenchState state) {
        state.ringBuffer.putNP(state);
        return state.ringBuffer;
    }


    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public Object tryTake1C(BenchState state) {
        return state.ringBuffer.tryTake1C();
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public Object tryTakeNC(BenchState state) {
        return state.ringBuffer.tryTake1C();
    }
}
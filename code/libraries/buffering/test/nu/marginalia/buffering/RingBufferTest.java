package nu.marginalia.buffering;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;

@Tag("slow")
public class RingBufferTest {

    @Test
    public void test1PNC() throws InterruptedException {
        RingBuffer<Long> buffer = new RingBuffer<>(16);

        Instant start = Instant.now();

        Thread a = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000_000; i++) {
                while (!buffer.put((long) i));
            };
        });


        Thread b = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000_000; i++) {
                buffer.takeNC();
            };
        });

        a.join();
        b.join();

        System.out.println(Duration.between(start, Instant.now()));
    }

    @Test
    public void test1P1C() throws InterruptedException {
        RingBuffer<Long> buffer = new RingBuffer<>(16);

        Instant start = Instant.now();

        Thread a = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000_000; i++) {
                while (!buffer.put((long) i));
            };
        });


        Thread b = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000_000; i++) {
                buffer.take1C();
            };
        });

        a.join();
        b.join();

        System.out.println(Duration.between(start, Instant.now()));
    }

    @Test
    public void testNPNC() throws InterruptedException {
        RingBuffer<Long> buffer = new RingBuffer<>(16);

        Instant start = Instant.now();

        Thread a = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000_000; i++) {
                while (!buffer.putNP(null));
            };
        });


        Thread b = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000_000; i++) {
                buffer.takeNC();
            };
        });

        a.join();
        b.join();

        System.out.println(Duration.between(start, Instant.now()));
    }


    @Test
    public void testLong() throws InterruptedException {
        LongRingBuffer buffer = new LongRingBuffer(256);

        Instant start = Instant.now();

        Thread a = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000_000; i++) {
                buffer.put(i);
            };
        });


        Thread b = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000_000; i++) {
                buffer.take();
            };
        });

        a.join();
        b.join();

        System.out.println(Duration.between(start, Instant.now()));
    }

    @Disabled
    @Test
    public void testABQ() throws InterruptedException {
        ArrayBlockingQueue<Long> buffer = new ArrayBlockingQueue<>(16);

        Instant start = Instant.now();

        Thread a = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000_000; i++) {
                while (!buffer.offer((long) i));
            };
        });


        Thread b = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000_000; i++) {
                while (buffer.poll() == null);
            };
        });

        a.join();
        b.join();

        System.out.println(Duration.between(start, Instant.now()));
    }
}

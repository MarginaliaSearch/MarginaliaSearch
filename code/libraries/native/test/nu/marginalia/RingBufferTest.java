package nu.marginalia;

import nu.marginalia.asyncio.LongRingBufferSPSC;
import nu.marginalia.asyncio.RingBufferNPNC;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;

@Tag("slow")
public class RingBufferTest {

    @Test
    public void test() throws InterruptedException {
        RingBufferNPNC<Long> buffer = new RingBufferNPNC<>(16);

        Instant start = Instant.now();

        Thread a = Thread.ofPlatform().start(() -> {
            long lock = buffer.lockWrite();
            for (int i = 0; i < 1_000_000; i++) {
                while (!buffer.put((long) i, lock));
            };
            buffer.unlockWrite(lock);
        });


        Thread b = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 1_000_000; i++) {
                buffer.take();
            };
        });

        a.join();
        b.join();

        System.out.println(Duration.between(start, Instant.now()));
    }

    @Test
    public void test3() throws InterruptedException {
        RingBufferNPNC<Long> buffer = new RingBufferNPNC<>(16);

        Instant start = Instant.now();

        Thread a = Thread.ofPlatform().start(() -> {
            long lock = buffer.lockWrite();
            for (int i = 0; i < 1_000_000; i++) {
                while (!buffer.put(null, lock));
            };
            buffer.unlockWrite(lock);
        });


        Thread b = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 1_000_000; i++) {
                buffer.take();
            };
        });

        a.join();
        b.join();

        System.out.println(Duration.between(start, Instant.now()));
    }


    @Test
    public void testLong() throws InterruptedException {
        LongRingBufferSPSC buffer = new LongRingBufferSPSC(256);

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
    @Test
    public void test2() throws InterruptedException {
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

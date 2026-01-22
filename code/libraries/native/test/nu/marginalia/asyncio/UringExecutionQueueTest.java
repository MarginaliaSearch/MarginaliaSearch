package nu.marginalia.asyncio;

import nu.marginalia.ffi.LinuxSystemCalls;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

class UringExecutionQueueTest {
    @Test
    @Disabled
    public void test() {
        int fd = LinuxSystemCalls.openBuffered(Path.of("/home/vlofgren/Downloads/pycharm-2025.3.tar.gz"));

        List<MemorySegment> buffers = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            buffers.add(Arena.ofAuto().allocate(4096, 4096));
        }

        try (var eq = new UringExecutionQueue(16)) {
            Instant start = Instant.now();
            for (int i = 0;;i++) {
                if (i > 262144) {
                    i = 0;
                    System.out.println(Duration.between(start, Instant.now()));
                    start = Instant.now();
                }

                eq.submit(i, new AsyncReadRequest(fd, buffers.get(i % 1000), ThreadLocalRandom.current().nextInt(0, 262144)*4096L));

            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        finally {
            LinuxSystemCalls.closeFd(fd);
        }
    }
}
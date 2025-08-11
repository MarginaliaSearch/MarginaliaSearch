package nu.marginalia.uring;

import nu.marginalia.asyncio.AsyncReadRequest;
import nu.marginalia.asyncio.UringExecutionQueue;
import nu.marginalia.ffi.LinuxSystemCalls;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;

class UringExecutionQueueTest {
    @Test
    public void test() {
        int fd = LinuxSystemCalls.openDirect(Path.of("/home/vlofgren/test.dat"));
        MemorySegment ms = Arena.ofAuto().allocate(4096, 4096);
        try (var eq = new UringExecutionQueue(128)) {
            for (int i = 0;;i++) {
                eq.submit(i, List.of(
                        new AsyncReadRequest(fd, ms, 0),
                        new AsyncReadRequest(fd, ms, 0),
                        new AsyncReadRequest(fd, ms, 0),
                        new AsyncReadRequest(fd, ms, 0),
                        new AsyncReadRequest(fd, ms, 0)
                        ));
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        finally {
            LinuxSystemCalls.closeFd(fd);
        }
    }
}
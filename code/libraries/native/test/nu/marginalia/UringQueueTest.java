package nu.marginalia;

import nu.marginalia.ffi.IoUring;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.uring.UringQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

class UringQueueTest {
    Path file;

    @BeforeEach
    void setUp() throws IOException {
        file = Files.createTempFile("uring", "dat");

        byte[] contents = new byte[8192];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = (byte) i;
        }
        Files.write(file, contents);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(file);
    }

    @Test
    public void testReadBatchRaw() {
        Assumptions.assumeTrue(IoUring.isAvailable);

        int n = 8;
        int fd = LinuxSystemCalls.openBuffered(file);
        var uring = UringQueue.open(fd, 16);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment slab = arena.allocate(32L * n, 8);
            MemorySegment buffers = arena.allocate(8L * n, 8);
            MemorySegment sizes = arena.allocate(4L * n, 8);
            MemorySegment offsets = arena.allocate(8L * n, 8);

            for (int i = 0; i < n; i++) {
                buffers.setAtIndex(JAVA_LONG, i, slab.address() + 32L * i);
                sizes.setAtIndex(JAVA_INT, i, 32);
                offsets.setAtIndex(JAVA_LONG, i, 32L * i);
            }

            int ret = IoUring.readBatchRaw(uring, n, buffers.address(), sizes.address(), offsets.address());
            Assertions.assertEquals(n, ret);

            for (int i = 0; i < 32 * n; i++) {
                Assertions.assertEquals((byte) i, slab.get(JAVA_BYTE, i));
            }
        }
        finally {
            uring.close();
            LinuxSystemCalls.closeFd(fd);
        }
    }

    @Test
    public void testReadFixedBatchRaw() {
        Assumptions.assumeTrue(IoUring.isAvailable);

        int n = 8;
        int fd = LinuxSystemCalls.openBuffered(file);
        var uring = UringQueue.open(fd, 16);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment slab = arena.allocate(32L * n, 8);
            MemorySegment buffers = arena.allocate(8L * n, 8);
            MemorySegment sizes = arena.allocate(4L * n, 8);
            MemorySegment offsets = arena.allocate(8L * n, 8);

            IoUring.registerBuffer(uring, slab.address(), slab.byteSize());

            for (int i = 0; i < n; i++) {
                buffers.setAtIndex(JAVA_LONG, i, slab.address() + 32L * i);
                sizes.setAtIndex(JAVA_INT, i, 32);
                offsets.setAtIndex(JAVA_LONG, i, 32L * (n - 1 - i));
            }

            int ret = IoUring.readFixedBatchRaw(uring, n, buffers.address(), sizes.address(), offsets.address());
            Assertions.assertEquals(n, ret);

            for (int i = 0; i < n; i++) {
                Assertions.assertEquals((byte) (32 * (n - 1 - i)), slab.get(JAVA_BYTE, 32L * i));
            }
        }
        finally {
            uring.close();
            LinuxSystemCalls.closeFd(fd);
        }
    }
}

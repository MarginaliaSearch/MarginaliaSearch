package nu.marginalia;

import nu.marginalia.array.DirectFileReader;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.uring.UringFileReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.List;

public class NativeAlgosTest {
    @Test
    public void test() throws IOException {
        LongArray array = LongArrayFactory.mmapForWritingShared(Path.of("/tmp/test"), 1024);
        for (int i = 0; i < 1024; i++) {
            array.set(i, i);
        }
        array.close();

        var ms = Arena.global().allocate(512, 8);

        int fd = LinuxSystemCalls.openDirect(Path.of("/tmp/test"));
        int ret = LinuxSystemCalls.readAt(fd, ms, 512);
        System.out.println(ret);
        System.out.println(ms.byteSize());
        LinuxSystemCalls.closeFd(fd);

        var array2 = LongArrayFactory.wrap(ms);
        for (int i = 0; i < array2.size(); i++) {
            System.out.println(i + ": " + array2.get(i));
        }

    }

    @Test
    void testDirectFileReader() throws IOException {
        LongArray array = LongArrayFactory.mmapForWritingShared(Path.of("/tmp/test"), 1024);
        for (int i = 0; i < 1024; i++) {
            array.set(i, i);
        }
        array.close();


        try (var dfr = new DirectFileReader(Path.of("/tmp/test"))) {
            LongArray array2 = LongArrayFactory.onHeapConfined(64);
            dfr.readAligned(array2, 0);
            for (int i = 0; i < array2.size(); i++) {
                System.out.println(i + ": " + array2.get(i));
            }
        }

        var alignedBuffer = Arena.ofAuto().allocate(4096, 4096);
        try (var dfr = new DirectFileReader(Path.of("/tmp/test"))) {
            MemorySegment dest = Arena.ofAuto().allocate(504, 1);
            dfr.readUnaligned(dest, alignedBuffer, 8);

            for (int i = 0; i < dest.byteSize(); i+=8) {
                System.out.println(i + ": " + dest.get(ValueLayout.JAVA_LONG, i));
            }

            dfr.readUnaligned(dest, alignedBuffer, 4000);
            for (int i = 0; i < dest.byteSize(); i+=8) {
                System.out.println(i + ": " + dest.get(ValueLayout.JAVA_LONG, i));
            }
        }
    }

    @Test
    void testAioFileReader() throws IOException {
        LongArray array = LongArrayFactory.mmapForWritingShared(Path.of("/tmp/test"), 1024);
        for (int i = 0; i < 1024; i++) {
            array.set(i, i);
        }
        array.close();

        try (var dfr = new UringFileReader(Path.of("/tmp/test"),  false)) {
            MemorySegment buf1 = Arena.ofAuto().allocate(32, 8);
            MemorySegment buf2 = Arena.ofAuto().allocate(16, 8);

            dfr.read(List.of(buf1, buf2), List.of(0L, 8L));

            for (int i = 0; i < buf1.byteSize(); i+=8) {
                System.out.println(buf1.get(ValueLayout.JAVA_LONG, i));
            }

            for (int i = 0; i < buf2.byteSize(); i+=8) {
                System.out.println(buf2.get(ValueLayout.JAVA_LONG, i));
            }
        }

    }
}

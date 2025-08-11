package nu.marginalia;

import nu.marginalia.uring.UringFileReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class UringFileReaderTest {
    Path testFile;
    @BeforeEach
    public void setUp() throws IOException {
        testFile = Files.createTempFile("UringFileReaderTest", ".dat");
    }
    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(testFile);
    }

    void createTestFileWithLongs(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size * 8);
        for (int i = 0; i < size; i++) {
            buffer.putLong(i);
        }
        buffer.flip();
        try (var fc = Files.newByteChannel(testFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (buffer.hasRemaining())
                fc.write(buffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testUringFileReader() throws IOException {

        createTestFileWithLongs(1024);

        try (var dfr = new UringFileReader(testFile,  false)) {
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

    @Test
    void testUringFileReaderUnaligned() throws IOException {
        createTestFileWithLongs(65536);

        try (var dfr = new UringFileReader(testFile,  true)) {
            var ret = dfr.readUnalignedInDirectMode(Arena.ofAuto(),
                    new long[] { 10*8, 20*8, 5000*8, 5100*8},
                    new int[] { 32*8, 10*8, 100*8, 100*8},
                    4096);
            System.out.println(ret.get(0).get(ValueLayout.JAVA_LONG, 0));
            System.out.println(ret.get(1).get(ValueLayout.JAVA_LONG, 0));
            System.out.println(ret.get(2).get(ValueLayout.JAVA_LONG, 0));
            System.out.println(ret.get(3).get(ValueLayout.JAVA_LONG, 0));
        }

    }
}

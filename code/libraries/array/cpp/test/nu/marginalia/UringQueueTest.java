package nu.marginalia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class UringQueueTest {
    Path file;
    @BeforeEach
    void setUp() throws IOException {
        file = Files.createTempFile("uring", "dat");
        Files.write(file, new byte[8192]);
    }
    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(file);
    }

    @Test
    public void testSunnyDay() throws IOException {
        int fd = NativeAlgos.openBuffered(file);

        List<MemorySegment> segments = new ArrayList<>();
        List<Long> offsets = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            segments.add(Arena.ofAuto().allocate(32));
            offsets.add(32L*i);
        }
        var uring = UringQueue.open(fd, 16);
        uring.read(segments, offsets, false);
        uring.close();

    }
}
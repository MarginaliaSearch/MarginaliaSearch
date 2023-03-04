package nu.marginalia.array;

import nu.marginalia.array.page.PagingIntArray;
import nu.marginalia.array.page.PagingLongArray;
import nu.marginalia.array.scheme.SequentialPartitioningScheme;
import nu.marginalia.util.test.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagingIntArrayTest {
    Path tempDir;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @AfterEach
    public void tearDown() {
        TestUtil.clearTempDir(tempDir);
    }

    @Test
    public void testReadLoad() throws IOException {
        SequentialPartitioningScheme partitioningScheme = new SequentialPartitioningScheme(7);
        Path file = Files.createTempFile(tempDir, "test", "dat");

        var heapArray = PagingIntArray.newOnHeap(partitioningScheme, 51);
        for (int i = 0; i < 51; i++) {
            heapArray.set(i, 2 * i);
        }
        heapArray.write(file);


        var diskArray = PagingIntArray.mapFileReadOnly(partitioningScheme, file);
        for (int i = 0; i < 51; i++) {
            assertEquals(2 * i, diskArray.get(i));
        }

    }

    @Test
    public void testReadLoadLong() throws IOException {
        SequentialPartitioningScheme partitioningScheme = new SequentialPartitioningScheme(7);
        Path file = Files.createTempFile(tempDir, "test", "dat");

        var heapArray = PagingLongArray.newOnHeap(partitioningScheme, 51);
        for (int i = 0; i < 51; i++) {
            heapArray.set(i, 2 * i);
        }
        heapArray.write(file);


        var diskArray = PagingLongArray.mapFileReadOnly(partitioningScheme, file);
        for (int i = 0; i < 51; i++) {
            assertEquals(2 * i, diskArray.get(i));
        }
    }

    @Test
    public void testReadFromFileChannel() throws IOException {
        SequentialPartitioningScheme partitioningScheme = new SequentialPartitioningScheme(7);
        Path file = Files.createTempFile(tempDir, "test", "dat");

        var heapArray = PagingLongArray.newOnHeap(partitioningScheme, 51);
        for (int i = 0; i < 51; i++) {
            heapArray.set(i, 2 * i);
        }
        heapArray.write(file);

        try (var channel = (FileChannel) Files.newByteChannel(file, StandardOpenOption.READ)) {

            var heapArray2 = PagingLongArray.newOnHeap(partitioningScheme, 51);
            heapArray2.transferFrom(channel, 10, 7, 20);

            var heapArray3 = PagingLongArray.newPartitionedOnHeap(partitioningScheme, 51);
            heapArray3.transferFrom(channel, 10, 7, 20);

            for (int i = 0; i < 51; i++) {
                System.out.println(i + ":" + heapArray2.get(i));
                assertEquals(heapArray3.get(i), heapArray2.get(i));
            }
        }
    }
}
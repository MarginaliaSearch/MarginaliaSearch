package nu.marginalia.wmsa.edge.index.service;

import lombok.SneakyThrows;
import nu.marginalia.util.multimap.MultimapFileLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultimapFileTest {
    File tmp;
    File tmp2;

    @BeforeEach @SneakyThrows
    public void setUp() {

        tmp = Files.createTempFile("test", "test").toFile();
        tmp2 = Files.createTempFile("test", "test").toFile();

    }
    @AfterEach
    public void tearDown() {
        tmp.delete();
        tmp2.delete();
    }

    @SneakyThrows
    @Test
    void transfer() {
        ByteBuffer buf = ByteBuffer.allocateDirect(77);
        try (var source = MultimapFileLong.forOutput(tmp.toPath(), 1024);
             var dest = new MultimapFileLong(tmp, FileChannel.MapMode.READ_WRITE, 1024, 8);
        ) {
            for (int i = 0; i < 1024; i++) {
                source.put(i, i);
            }
            source.force();
            dest.transferFromFileChannel(new RandomAccessFile(tmp, "r").getChannel(), 11, 55, 100);
            for (int i = 0; i < 45; i++) {
                System.out.println("source=" + (11+i) + ", dest = " + dest.get(11+i));
                assertEquals(55+i, dest.get(11+i));
            }
        }
    }

    @SneakyThrows
    @Test
    void put() {
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 32, 8, false);
        for (int i = 0; i < 32; i++) {
            file.put(i, i);
        }
        for (int i = 0; i < 32; i++) {
            assertEquals(i, file.get(i));
        }
    }

    @SneakyThrows
    @Test
    void read() {
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 32, 8, false);
        for (int i = 0; i < 32; i++) {
            file.put(i, i);
        }

        for (int i = 0; i < 32-6; i++) {
            long[] vals = new long[6];
            file.read(vals, i);
            for (int j = 0; j < 6; j++) {
                assertEquals(i+j, vals[j]);
            }
        }

    }

    @Test
    void write() throws IOException {
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 32, 8, false);

        for (int i = 0; i < 32-6; i++) {
            file.write(new long[] { 0,1,2,3,4,5}, i);
            for (int j = 0; j < 6; j++) {
                assertEquals(j, file.get(i+j));
            }
        }

    }

    @Test
    void sortInternal() throws IOException {
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 32, 8, false);
        var sorter = file.createSorter(Path.of("/tmp"), 16);
        var searcher = file.createSearcher();
        for (int i = 0; i < 32; i++) {
            file.put(i, 32-i);
        }

        sorter.sort( 2, 14);

        for (int i = 2+1; i < 16; i++) {
            assertTrue(file.get(i) > file.get(i-1));
            assertTrue(searcher.binarySearch(file.get(i), 2, 18));
        }
    }

    @Test
    void sortExternal() throws IOException {
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 32, 8, false);
        var sorter = file.createSorter(Path.of("/tmp"), 2);
        var searcher = file.createSearcher();

        for (int i = 0; i < 32; i++) {
            file.put(i, 32-i);
        }

        sorter.sort( 2, 14);
        file.force();

        for (int i = 2+1; i < 16; i++) {
            assertTrue(file.get(i) > file.get(i-1));
            assertTrue(searcher.binarySearch(file.get(i), 2, 18));
        }
    }

    @Test
    void close() {
    }
}
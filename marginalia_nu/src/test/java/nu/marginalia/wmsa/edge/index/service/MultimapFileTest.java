package nu.marginalia.wmsa.edge.index.service;

import lombok.SneakyThrows;
import nu.marginalia.util.multimap.MultimapFileLong;
import org.apache.commons.lang3.ArrayUtils;
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
             var dest = new MultimapFileLong(tmp, FileChannel.MapMode.READ_WRITE, 1024, 8)
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
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 32, 8);
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
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 32, 8);
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
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 32, 8);

        for (int i = 0; i < 32-6; i++) {
            file.write(new long[] { 0,1,2,3,4,5}, i);
            for (int j = 0; j < 6; j++) {
                assertEquals(j, file.get(i+j));
            }
        }

    }

    @Test
    void testQuickSort() throws IOException {
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 128, 8);
        var sorter = file.createSorter(Path.of("/tmp"), 16, 2);

        for (int start = 0; start < 8; start+=2) {
            System.out.println("~");
            for (int end = start; end < 128; end+=2) {
                for (int i = 0; i < 128; i+=2) {
                    file.put(i, -i/2);
                    file.put(i+1, i/2);
                }
                sorter.quickSortLH(start, end);
                for (int i = start+2; i < end; i+=2) {

                    System.out.println("**" + i);
                    System.out.println(file.get(i-2));
                    System.out.println(file.get(i-1));
                    System.out.println(file.get(i));
                    System.out.println(file.get(i+1));

                    assertTrue(file.get(i-2) <= file.get(i));
                    assertEquals(file.get(i+1), -file.get(i));
                }
                System.out.println("~");
            }
        }

    }

    @Test
    void testSort() throws IOException {
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 128, 8);
        var sorter = file.createSorter(Path.of("/tmp"), 1024, 2);

        long[] values = new long[65536];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }
        ArrayUtils.shuffle(values);

        int start = 6;
        System.out.println(start);
        for (int end = start+2; end < values.length; end+=100) {

            for (long i = 0; i < end+1; i+=2) {
                file.put(i, values[(int)i/2]);
                file.put(i+1, i/2);
            }


            file.put(start-2, 100000);
            file.put(end, 1);
            sorter.sortRange(start, end);

            for (int i = start+2; i < end; i+=2) {
                assertTrue(file.get(i-2) < file.get(i));
            }

            assertEquals(100000, file.get(start-2));
            assertEquals(1, file.get(end));
        }

    }

    @Test
    void testInsertionSort() throws IOException {
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 128, 8);
        var sorter = file.createSorter(Path.of("/tmp"), 16, 2);

        for (int start = 2; start < 8; start+=2) {
            for (int end = start+2; end < 126; end+=2) {
                for (int i = 0; i < 128; i+=2) {
                    file.put(i, -(128-i/2));
                    file.put(i+1, (128-i/2));
                }
                file.put(0, 0xFFFF_FFFFL);
                file.put(end, 0x7FFF_FFFFL);
                sorter.insertionSort(start, (end - start)/2);
                assertEquals(0xFFFF_FFFFL, file.get(0));
                assertEquals(file.get(end), 0x7FFF_FFFFL);
                for (int i = start+2; i < end; i+=2) {
                    assertTrue(file.get(i-2) <= file.get(i));
                    assertEquals(file.get(i+1), -file.get(i));
                }
            }
        }
    }

    @Test
    void testMergeSort() throws IOException {
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 128, 8);
        var sorter = file.createSorter(Path.of("/tmp"), 16, 2);

        for (int start = 0; start < 512; start+=18) {
            System.out.println(start);
            for (int end = start+2; end < 8192; end+=68) {
                for (int i = 0; i < 8192; i+=2) {
                    file.put(i, -i/2);
                    file.put(i+1, i/2);
                }
                sorter.mergeSort(start, end-start);

                assertEquals(file.get(start+1), -file.get(start));
                for (int i = start+2; i < end; i+=2) {
//                    System.out.println(file.get(i-2) + "," + file.get(i));
                    assertTrue(file.get(i-2) <= file.get(i));

//                    System.out.println(file.get(i+1) + ":" + -file.get(i));
                    assertEquals(file.get(i+1), -file.get(i));
                }
            }
        }
    }

    @Test
    void sortInternal() throws IOException {
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 32, 8);
        var sorter = file.createSorter(Path.of("/tmp"), 16, 1);
        var searcher = file.createSearcher();
        for (int i = 0; i < 32; i++) {
            file.put(i, 32-i);
        }

        sorter.sortRange( 2, 16);

        for (int i = 2+1; i < 16; i++) {
            assertTrue(file.get(i) > file.get(i-1));
            assertTrue(searcher.binarySearchTest(file.get(i), 2, 16));
        }
    }

    @Test
    void sortExternal() throws IOException {
        var file = new MultimapFileLong(new RandomAccessFile(tmp, "rw"), FileChannel.MapMode.READ_WRITE, 32, 8);
        var sorter = file.createSorter(Path.of("/tmp"), 2, 1);
        var searcher = file.createSearcher();

        for (int i = 0; i < 32; i++) {
            file.put(i, 32-i);
        }

        sorter.sortRange( 2, 16);
        file.force();

        for (int i = 2+1; i < 16; i++) {
            assertTrue(file.get(i) > file.get(i-1));
            assertTrue(searcher.binarySearchTest(file.get(i), 2, 16));
        }
    }


    @Test
    void close() {
    }
}
package nu.marginalia.util.btree;

import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.btree.model.BTreeHeader;
import nu.marginalia.util.multimap.MultimapFileLong;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BTreeWriterTestCachedReader {

    final BTreeContext ctx = new BTreeContext(4,  2, 0xFFFF_FFFF_FFFF_FFFFL, 3);
    final BTreeWriter writer = new BTreeWriter(null, ctx);

    Logger logger = LoggerFactory.getLogger(getClass());
    @Test
    void testSmallDataBlock() {
        var header = writer.makeHeader(1024, ctx.BLOCK_SIZE_WORDS()/2);
        assertEquals(1024 + BTreeHeader.BTreeHeaderSizeLongs, header.dataOffsetLongs());
        assertEquals(header.dataOffsetLongs(), header.indexOffsetLongs());
    }

    @Test
    void testLayerCount() {
        int wsq = ctx.BLOCK_SIZE_WORDS()*ctx.BLOCK_SIZE_WORDS();
        int wcub = ctx.BLOCK_SIZE_WORDS()*ctx.BLOCK_SIZE_WORDS()*ctx.BLOCK_SIZE_WORDS();

        assertEquals(2, writer.makeHeader(1024, wsq-1).layers());
        assertEquals(2, writer.makeHeader(1024, wsq).layers());
        assertEquals(3, writer.makeHeader(1024, wsq+1).layers());

        assertEquals(3, writer.makeHeader(1024, wcub-1).layers());
        assertEquals(3, writer.makeHeader(1024, wcub).layers());
        assertEquals(4, writer.makeHeader(1024, wcub+1).layers());
    }

    @Test
    void testLayerOffset() {
        int wcub = ctx.BLOCK_SIZE_WORDS()*ctx.BLOCK_SIZE_WORDS()*ctx.BLOCK_SIZE_WORDS();
        System.out.println(writer.makeHeader(1025, wcub).relativeIndexLayerOffset(ctx, 0));
        System.out.println(writer.makeHeader(1025, wcub).relativeIndexLayerOffset(ctx, 1));
        System.out.println(writer.makeHeader(1025, wcub).relativeIndexLayerOffset(ctx, 2));

        for (int i = 0; i < 1024; i++) {
            var header = writer.makeHeader(0, i);


            printTreeLayout(i, header, ctx);

            if (header.layers() >= 1) {
                assertEquals(1, ctx.indexLayerSize(i, header.layers() - 1) / ctx.BLOCK_SIZE_WORDS());
            }
        }
    }

    private void printTreeLayout(int numEntries, BTreeHeader header, BTreeContext ctx) {
        StringJoiner sj = new StringJoiner(",");
        for (int l = 0; l < header.layers(); l++) {
            sj.add(""+ctx.indexLayerSize(numEntries, l)/ctx.BLOCK_SIZE_WORDS());
        }
        System.out.println(numEntries + ":" + sj);
    }

    @Test
    public void testWriteEntrySize2() throws IOException {

        var tempFile = Files.createTempFile(Path.of("/tmp"), "tst", "dat");
        Set<Integer> toPut = new HashSet<>();

        for (int i = 0; i < 500; i++) {
            while (!toPut.add((int)(Integer.MAX_VALUE * Math.random())));
        }

        int[] data = toPut.stream().mapToInt(Integer::valueOf).sorted().toArray();

        try {
            RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw");
            MultimapFileLong mmf = new MultimapFileLong(raf, FileChannel.MapMode.READ_WRITE, 10000, 1000);

            {
                var writer = new BTreeWriter(mmf, ctx);
                writer.write(0, toPut.size(), (slice) -> {
                    for (int i = 0; i < data.length; i++) {
                        slice.put(2L*i, data[i]);
                        slice.put( 2L*i + 1, i);
                    }
                });
                mmf.force();
            }

            {
                var reader = new CachingBTreeReader(mmf, ctx);
                var header = reader.getHeader(0);
                var cache = reader.prepareCache(header);
                for (int i = 0; i < data.length; i++) {
                    long offset = reader.findEntry(cache, data[i]);
                    assertTrue(offset >= 0, "Negative offset for " + i + " -> " + offset);
                    assertEquals(i, mmf.get(offset+1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Files.delete(tempFile);
        }
    }

    @Test
    public void testWriteEntrySize2Small() throws IOException {

        var tempFile = Files.createTempFile(Path.of("/tmp"), "tst", "dat");
        Set<Integer> toPut = new HashSet<>();

        for (int i = 0; i < 5; i++) {
            while (!toPut.add((int)(Integer.MAX_VALUE * Math.random())));
        }

        int[] data = toPut.stream().mapToInt(Integer::valueOf).sorted().toArray();

        try {
            RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "rw");
            MultimapFileLong mmf = new MultimapFileLong(raf, FileChannel.MapMode.READ_WRITE, 10000, 1000);

            {
                var writer = new BTreeWriter(mmf, ctx);
                writer.write( 0, toPut.size(), (slice) -> {
                    for (int i = 0; i < data.length; i++) {
                        slice.put(2L*i, data[i]);
                        slice.put(2L*i + 1, i);
                    }
                });
                mmf.force();
            }

            {
                var reader = new CachingBTreeReader(mmf, ctx);
                var header = reader.getHeader(0);
                var cache = reader.prepareCache(header);
                for (int i = 0; i < data.length; i++) {
                    long offset = reader.findEntry(cache, data[i]);
                    assertTrue(offset >= 0, "Negative offset for " + i + " -> " + offset);
                    assertEquals(i, mmf.get(offset+1));
                }

                for (int i = 0; i < 500; i++) {
                    long val = (long)(Long.MAX_VALUE * Math.random());
                    while (toPut.contains((int)val)) val = (long)(Long.MAX_VALUE * Math.random());
                    assertEquals(-1, reader.findEntry(cache, val));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Files.delete(tempFile);
        }
    }


    @Test
    public void testWriteEqualityNotMasked() throws IOException {
        for (int bs = 2; bs <= 4; bs++) {
            var tempFile = Files.createTempFile(Path.of("/tmp"), "tst", "dat");
            Set<Long> toPut = new HashSet<>();

            var ctx = new BTreeContext(5, 1, ~0, bs);

            for (int i = 0; i < 500; i++) {
                while (!toPut.add((long) (Long.MAX_VALUE * Math.random()))) ;
            }

            long[] data = toPut.stream().mapToLong(Long::valueOf).sorted().toArray();

            try (MultimapFileLong mmf = MultimapFileLong.forOutput(tempFile, 1000)) {
                {
                    var writer = new BTreeWriter(mmf, ctx);
                    writer.write(0, toPut.size(), (slice) -> {
                        for (int i = 0; i < data.length; i++) {
                            slice.put(i, data[i]);
                        }
                    });
                    mmf.force();
                }

                {
                    var reader = new CachingBTreeReader(mmf, ctx);
                    var header = reader.getHeader(0);
                    var cache = reader.prepareCache(header);

                    printTreeLayout(toPut.size(), header, ctx);

                    for (int i = 0; i < data.length; i++) {
                        long offset = reader.findEntry(cache, data[i]);
                        assertTrue(offset >= 0, "Negative offset for " + i + " -> " + offset);
                        assertEquals(data[i], mmf.get(offset));
                    }

                    for (int i = 0; i < 500; i++) {
                        long val = (long) (Long.MAX_VALUE * Math.random());
                        while (toPut.contains(val)) val = (long) (Long.MAX_VALUE * Math.random());
                        assertEquals(-1, reader.findEntry(cache, val));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Files.delete(tempFile);
            }
        }
    }

    @Test
    public void testWriteEqualityMasked() throws IOException {

        for (int bs = 2; bs <= 4; bs++) {
            var tempFile = Files.createTempFile(Path.of("/tmp"), "tst", "dat");
            Set<Long> toPut = new HashSet<>();

            long mask = 0xFFFF_FFFF_0000_0000L;
            var ctx = new BTreeContext(5, 1, mask, bs);

            for (int i = 0; i < 500; i++) {
                while (!toPut.add((long) (Long.MAX_VALUE * Math.random()))) ;
            }

            long[] data = toPut.stream().mapToLong(Long::valueOf).sorted().toArray();

            try (MultimapFileLong mmf = MultimapFileLong.forOutput(tempFile, 1000)) {
                {
                    var writer = new BTreeWriter(mmf, ctx);
                    writer.write(0, toPut.size(), (slice) -> {
                        for (int i = 0; i < data.length; i++) {
                            slice.put(i, data[i]);
                        }
                    });
                    mmf.force();
                }

                {
                    var reader = new CachingBTreeReader(mmf, ctx);
                    var header = reader.getHeader(0);
                    var cache = reader.prepareCache(header);

                    printTreeLayout(toPut.size(), header, ctx);

                    for (int i = 0; i < data.length; i++) {
                        long offset = reader.findEntry(cache,data[i] & mask);
                        assertTrue(offset >= 0, "Negative offset for " + i + " -> " + offset);
                        assertEquals(data[i], mmf.get(offset));
                    }

                    for (int i = 0; i < 500; i++) {
                        long val = (long) (Long.MAX_VALUE * Math.random());
                        while (toPut.contains(val)) val = (long) (Long.MAX_VALUE * Math.random());
                        assertEquals(-1, reader.findEntry(cache, val & mask));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Files.delete(tempFile);
            }
        }
    }

    @Test
    public void testWriteTwoEqualityMasked() throws IOException {

        for (int bs = 2; bs <= 4; bs++) {
            var tempFile = Files.createTempFile(Path.of("/tmp"), "tst", "dat");
            Set<Long> toPut = new HashSet<>();

            long mask = 0xFFFF_FFFF_0000_0000L;
            var ctx = new BTreeContext(5, 2, mask, bs);

            for (int i = 0; i < 500; i++) {
                while (!toPut.add((long) (Long.MAX_VALUE * Math.random()))) ;
            }

            long[] data = toPut.stream().mapToLong(Long::valueOf).sorted().toArray();

            try (MultimapFileLong mmf = MultimapFileLong.forOutput(tempFile, 1000)) {
                {
                    var writer = new BTreeWriter(mmf, ctx);
                    writer.write(0, toPut.size(), (slice) -> {
                        for (int i = 0; i < data.length; i++) {
                            slice.put(i*2L, data[i]);
                            slice.put(i*2L+1, i);
                        }
                    });
                    mmf.force();
                }

                {
                    var reader = new CachingBTreeReader(mmf, ctx);
                    var header = reader.getHeader(0);
                    var cache = reader.prepareCache(header);

                    printTreeLayout(toPut.size(), header, ctx);

                    for (int i = 0; i < data.length; i++) {
                        long offset = reader.findEntry(cache, data[i] & mask);
                        assertTrue(offset >= 0, "Negative offset for " + i + " -> " + offset);
                        assertEquals(data[i], mmf.get(offset));
                        assertEquals(i, mmf.get(offset+1));
                    }

                    for (int i = 0; i < 500; i++) {
                        long val = (long) (Long.MAX_VALUE * Math.random());
                        while (toPut.contains(val)) val = (long) (Long.MAX_VALUE * Math.random());
                        assertEquals(-1, reader.findEntry(cache,val & mask));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Files.delete(tempFile);
            }
        }
    }



}
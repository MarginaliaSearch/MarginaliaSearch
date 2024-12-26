package nu.marginalia.btree;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.btree.model.BTreeBlockSize;
import nu.marginalia.btree.model.BTreeContext;
import nu.marginalia.btree.model.BTreeHeader;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BTreeWriterTest {



    @Test
    void testSmallDataBlock() {
        BTreeContext ctx = new BTreeContext(4,  2,  BTreeBlockSize.BS_64);
        BTreeWriter writer = new BTreeWriter(null, ctx);

        var header = writer.makeHeader(ctx, 1024, ctx.pageSize()/2);
        assertEquals(1024 + BTreeHeader.BTreeHeaderSizeLongs, header.dataOffsetLongs());
        assertEquals(header.dataOffsetLongs(), header.indexOffsetLongs());
    }

    @Test
    void testLayerCount() {
        BTreeContext ctx = new BTreeContext(4,  2,  BTreeBlockSize.BS_64);
        BTreeWriter writer = new BTreeWriter(null, ctx);

        int wsq = ctx.pageSize()*ctx.pageSize();
        int wcub = ctx.pageSize()*ctx.pageSize()*ctx.pageSize();

        assertEquals(2, writer.makeHeader(ctx, 1024, wsq-1).layers());
        assertEquals(2, writer.makeHeader(ctx, 1024, wsq).layers());
        assertEquals(3, writer.makeHeader(ctx, 1024, wsq+1).layers());

        assertEquals(3, writer.makeHeader(ctx, 1024, wcub-1).layers());
        assertEquals(3, writer.makeHeader(ctx, 1024, wcub).layers());
        assertEquals(4, writer.makeHeader(ctx, 1024, wcub+1).layers());
    }

    @Test
    void testLayerOffset() {
        BTreeContext ctx = new BTreeContext(4,  2,  BTreeBlockSize.BS_64);
        BTreeWriter writer = new BTreeWriter(null, ctx);

        int wcub = ctx.pageSize()*ctx.pageSize()*ctx.pageSize();
        System.out.println(writer.makeHeader(ctx,1025, wcub).relativeIndexLayerOffset(ctx, 0));
        System.out.println(writer.makeHeader(ctx,1025, wcub).relativeIndexLayerOffset(ctx, 1));
        System.out.println(writer.makeHeader(ctx,1025, wcub).relativeIndexLayerOffset(ctx, 2));

        for (int i = 0; i < 1024; i++) {
            var header = writer.makeHeader(ctx,0, i);


//            printTreeLayout(i, header, ctx);

            if (header.layers() >= 1) {
                assertEquals(1, ctx.indexLayerSize(i, header.layers() - 1) / ctx.pageSize());
            }
        }
    }

    private void printTreeLayout(int numEntries, BTreeHeader header, BTreeContext ctx) {
        StringJoiner sj = new StringJoiner(",");
        for (int l = 0; l < header.layers(); l++) {
            sj.add(""+ctx.indexLayerSize(numEntries, l)/ctx.pageSize());
        }
        System.out.println(numEntries + ":" + sj);
    }

    @Test
    public void testWriteEntrySize2() throws IOException {
        BTreeContext ctx = new BTreeContext(4,  2,  BTreeBlockSize.BS_64);

        var tempFile = Files.createTempFile("tst", "dat");

        int[] data = generateItems32(64);

        try {
            LongArray longArray = LongArray.allocate(10000);

            writeIntEntrySize2(data, ctx, longArray);

            var reader = new BTreeReader(longArray, ctx, 0);
            for (int i = 0; i < data.length; i++) {
                long offset = reader.findEntry(data[i]);
                assertTrue(offset >= 0, "Negative offset for " + i + " -> " + offset);
                offset += reader.getHeader().dataOffsetLongs();
                assertEquals(i, longArray.get(offset+1));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Files.delete(tempFile);
        }
    }

    @Test
    public void testWriteEntrySize2Small() throws IOException {
        BTreeContext ctx = new BTreeContext(4,  2,  BTreeBlockSize.BS_64);

        int[] data = generateItems32(5);
        Set<Integer> items = IntStream.of(data).boxed().collect(Collectors.toSet());

        LongArray array = LongArray.allocate(22000);

        writeIntEntrySize2(data, ctx, array);

        var reader = new BTreeReader(array, ctx, 0);
        for (int i = 0; i < data.length; i++) {
            long offset = reader.findEntry(data[i]);
            assertTrue(offset >= 0, "Negative offset for " + i + " -> " + offset);
            offset += reader.getHeader().dataOffsetLongs();
            assertEquals(array.get(offset+1), i);
        }

        for (int i = 0; i < 500; i++) {
            long val = (long)(Long.MAX_VALUE * Math.random());
            while (items.contains((int)val)) val = (long)(Long.MAX_VALUE * Math.random());
            assertTrue(reader.findEntry( val) < 0);
        }
    }

    @Test
    @Disabled // This test creates a 16 GB file in tmp
    public void veryLargeBTreeTest() throws IOException {
        var wordsBTreeContext = new BTreeContext(5, 2, BTreeBlockSize.BS_2048);
        Path file = Path.of("/tmp/large.dat");
        try (var la = LongArrayFactory.mmapForWritingConfined(file, wordsBTreeContext.calculateSize(1024*1024*1024))) {
            new BTreeWriter(la, wordsBTreeContext)
                    .write(0, 1024*1024*1024, wc -> {
                        for (long i = 0; i < 1024*1024*1024; i++) {
                            wc.set(2*i, i);
                            wc.set(2*i + 1, -i);
                        }
                    });
            System.out.println("Wrote");
            var reader = new BTreeReader(la, wordsBTreeContext, 0);

            for (int i = 0; i < 1204*1204*1024; i++) {
                long offset = reader.findEntry(i);
                assertEquals(2L*i, offset);
            }
        }
        finally {
            Files.delete(file);
        }
    }

    @Test
    public void testWriteEqualityNotMasked() throws IOException {
        for (int bs = 2; bs <= 4; bs++) {
            var ctx = new BTreeContext(5, 1, BTreeBlockSize.fromBitCount(bs));
            long[] data = generateItems64(500);
            Set<Long> items = LongStream.of(data).boxed().collect(Collectors.toSet());

            LongArray array = LongArray.allocate(22000);
            writeLongEntrySize1(data, ctx, array);

            var reader = new BTreeReader(array, ctx, 0);

//            printTreeLayout(data.length, reader.getHeader(), ctx);

            for (int i = 0; i < data.length; i++) {
                long offset = reader.findEntry(data[i]);
                assertTrue(offset >= 0, "Negative offset for " + i + " -> " + offset);
                offset += reader.getHeader().dataOffsetLongs();
                assertEquals(data[i], array.get(offset));
            }

            for (int i = 0; i < 500; i++) {
                long val = (long) (Long.MAX_VALUE * Math.random());
                while (items.contains(val)) val = (long) (Long.MAX_VALUE * Math.random());
                assertTrue(reader.findEntry( val) < 0);
            }
        }
    }

    public int[] generateItems32(int n) {
        return IntStream.generate(() -> (int) (Integer.MAX_VALUE * Math.random())).distinct().limit(n).sorted().toArray();
    }

    public long[] generateItems64(int n) {
        return LongStream.generate(() -> (long) (Long.MAX_VALUE * Math.random())).distinct().limit(n).sorted().toArray();
    }

    private void writeIntEntrySize2(int[] data, BTreeContext ctx, LongArray array) throws IOException {
        var writer = new BTreeWriter(array, ctx);
        writer.write(0, data.length, (slice) -> {
            for (int i = 0; i < data.length; i++) {
                slice.set(2L*i, data[i]);
                slice.set(2L*i + 1, i);
            }
        });
    }

    private void writeLongEntrySize1(long[] data,  BTreeContext ctx, LongArray array) throws IOException {
        var writer = new BTreeWriter(array, ctx);
        writer.write(0, data.length, (slice) -> {
            for (int i = 0; i < data.length; i++) {
                slice.set(i, data[i]);
            }
        });
    }


}
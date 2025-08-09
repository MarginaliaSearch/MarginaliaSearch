package nu.marginalia.skiplist;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkipListWriterTest {
    Path docsFile;

    @BeforeEach
    void setUp() throws IOException {
        docsFile = Files.createTempFile(SkipListWriterTest.class.getSimpleName(), ".docs.dat");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(docsFile);
    }

    LongArray createArray(long[] keys, long[] values) {
        assert keys.length == values.length;
        MemorySegment ms = Arena.ofAuto().allocate(keys.length * 16);
        for (int i = 0; i < keys.length; i++) {
            ms.setAtIndex(ValueLayout.JAVA_LONG, 2L*i, keys[i]);
            ms.setAtIndex(ValueLayout.JAVA_LONG, 2L*i+1, values[i]);
        }
        return LongArrayFactory.wrap(ms);
    }

    LongArray createArray(Arena arena, long[] keys, long[] values) {
        assert keys.length == values.length;
        MemorySegment ms = arena.allocate(keys.length * 16);
        for (int i = 0; i < keys.length; i++) {
            ms.setAtIndex(ValueLayout.JAVA_LONG, 2L*i, keys[i]);
            ms.setAtIndex(ValueLayout.JAVA_LONG, 2L*i+1, values[i]);
        }
        return LongArrayFactory.wrap(ms);
    }

    @Test
    public void testWriteSingleBlock() throws IOException {
        long pos1, pos2;
        try (var writer = new SkipListWriter(docsFile)) {
            pos1 = writer.writeList(
                    createArray(new long[] {0,1,2,3,4,5,6,7}, new long[] { -0,-1,-2,-3,-4,-5,-6,-7}), 0, 8);
            pos2 = writer.writeList(
                    createArray(new long[] {0,1,2,3}, new long[] { -0,-1,-2,-3}), 4, 2);
        }

        System.out.println(pos1);
        System.out.println(pos2);

        try (var arr = LongArrayFactory.mmapForReadingConfined(docsFile)) {
            var ms = arr.getMemorySegment();

            var actual1 = SkipListReader.parseBlock(ms, (int) pos1);
            var expected1 = new SkipListReader.RecordView(8, 0,  SkipListConstants.FLAG_END_BLOCK,
                    new LongArrayList(),
                    new LongArrayList(new long[] { 0,1,2,3,4,5,6,7})
            );

            System.out.println(actual1);
            System.out.println(expected1);
            assertEquals(expected1, actual1);

            var actual2 = SkipListReader.parseBlock(ms, (int) pos2);
            var expected2 = new SkipListReader.RecordView(2, 0,  SkipListConstants.FLAG_END_BLOCK,
                    new LongArrayList(),
                    new LongArrayList(new long[] { 2,3}));

            System.out.println(actual2);
            System.out.println(expected2);
            assertEquals(expected2, actual2);
        }
    }

    @Test
    public void testTwoBlocks() throws IOException {
        long pos1;
        long[] keys = LongStream.range(0, (SkipListConstants.MAX_RECORDS_PER_BLOCK-32) * 2).toArray();
        long[] vals = LongStream.range(0, (SkipListConstants.MAX_RECORDS_PER_BLOCK-32) * 2).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            pos1 = writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        System.out.println(pos1);

        try (var arr = LongArrayFactory.mmapForReadingConfined(docsFile)) {
            LongArrayList allDocIds = new LongArrayList();
            LongArrayList allValues = new LongArrayList();

            var blocks = SkipListReader.parseBlocks(arr.getMemorySegment(), 0);

            for (var block : blocks) {
                System.out.println(block);
            }

            assertEquals(2, blocks.size());

            for (var block : blocks) {
                allDocIds.addAll(block.docIds());
            }

            LongList expectedAllDocIds = new LongArrayList(keys);
            LongList expectedAllValues = new LongArrayList();

            Assertions.assertEquals(expectedAllDocIds, allDocIds);
            Assertions.assertEquals(expectedAllValues, allValues);

            var rootBlock = blocks.getFirst();
            var secondBlock = blocks.get(1);

            LongList actualFp = rootBlock.fowardPointers();
            LongList expectedFp = new LongArrayList(new long[]{secondBlock.highestDocId()});

            Assertions.assertEquals(expectedFp, actualFp);
        }
    }

    @Test
    public void testTenBlocks() throws IOException {
        long pos1;
        long[] keys = LongStream.range(0, (SkipListConstants.MAX_RECORDS_PER_BLOCK-32)*10).toArray();
        long[] vals = LongStream.range(0, (SkipListConstants.MAX_RECORDS_PER_BLOCK-32)*10).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            pos1 = writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        System.out.println(pos1);

        try (var arr = LongArrayFactory.mmapForReadingConfined(docsFile)) {
            LongArrayList allDocIds = new LongArrayList();
            LongArrayList allValues = new LongArrayList();

            var blocks = SkipListReader.parseBlocks(arr.getMemorySegment(), 0);

            for (var block : blocks) {
                System.out.println(block);
            }

            assertEquals(10, blocks.size());

            for (var block : blocks) {
                allDocIds.addAll(block.docIds());
            }

            LongList expectedAllDocIds = new LongArrayList(keys);
            LongList expectedAllValues = new LongArrayList();

            Assertions.assertEquals(expectedAllDocIds, allDocIds);
            Assertions.assertEquals(expectedAllValues, allValues);

            for (int i = 0; i < blocks.size(); i++) {
                SkipListReader.RecordView block = blocks.get(i);
                for (int fci = 0; fci < block.fc(); fci++) {
                    int skipOffset = SkipListConstants.skipOffsetForPointer(fci);
                    Assertions.assertTrue(i + skipOffset < blocks.size());
                    Assertions.assertEquals(block.fowardPointers().getLong(fci), blocks.get(i+skipOffset).highestDocId());
                }
            }
        }

    }

    @Test
    public void testTenBlockFps() throws IOException {
        long pos1;
        long[] keys = LongStream.range(0, (SkipListConstants.MAX_RECORDS_PER_BLOCK-32)*10).toArray();
        long[] vals = LongStream.range(0, (SkipListConstants.MAX_RECORDS_PER_BLOCK-32)*10).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            pos1 = writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        System.out.println(pos1);

        try (var arr = LongArrayFactory.mmapForReadingConfined(docsFile)) {

            var blocks = SkipListReader.parseBlocks(arr.getMemorySegment(), 0);
            System.out.println(blocks);
            for (int i = 0; i + 1 < blocks.size(); i++) {
                if (blocks.get(i).fowardPointers().isEmpty()) {
                    continue;
                }
                var actual = blocks.get(i).fowardPointers().getFirst();
                var expected = blocks.get(i+1).docIds().getLast();
                assertEquals(actual, expected);
            }
        }
    }

    @Test
    public void testTenBlockFpsPadded() throws IOException {
        long pos1;
        long[] keys = LongStream.range(0, (SkipListConstants.MAX_RECORDS_PER_BLOCK-32)*10).toArray();
        long[] vals = LongStream.range(0, (SkipListConstants.MAX_RECORDS_PER_BLOCK-32)*10).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.padDocuments(64);
            pos1 = writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var arr = LongArrayFactory.mmapForReadingConfined(docsFile)) {

            var blocks = SkipListReader.parseBlocks(arr.getMemorySegment(), 0);
            for (int i = 0; i + 1 < blocks.size(); i++) {
                if (blocks.get(i).fowardPointers().isEmpty()) {
                    continue;
                }
                var actual = blocks.get(i).fowardPointers().getFirst();
                var expected = blocks.get(i+1).docIds().getLast();
                System.out.println(actual + " vs " + expected);
                assertEquals(actual, expected);
            }
        }
    }

    @Test
    public void testFpFuzz() throws IOException {

        long seedOffset = System.nanoTime();

        for (int seed = 0; seed < 100; seed++) {
            System.out.println("Seed: " + (seed + seedOffset));

            Random r = new Random(seed + seedOffset);

            LongSortedSet keyset = new LongAVLTreeSet();

            int nkeys = r.nextInt(SkipListConstants.BLOCK_SIZE/2, SkipListConstants.BLOCK_SIZE*4);
            while (keyset.size() < nkeys) {
                long val = r.nextLong(0, 10_000_000);

                keyset.add(val);
            }

            long[] keys = keyset.toLongArray();
            long[] qbs = new long[] { keys[r.nextInt(0, keys.length)] };

            long off = 0;
            try (var writer = new SkipListWriter(docsFile);
                 Arena arena = Arena.ofConfined()
            ) {
                writer.padDocuments(8*r.nextInt(0, SkipListConstants.BLOCK_SIZE/8));
                off = writer.writeList(createArray(arena, keys, keys), 0, keys.length);
            }


            try (var arr = LongArrayFactory.mmapForReadingConfined(docsFile)) {

                var blocks = SkipListReader.parseBlocks(arr.getMemorySegment(), 0);
                for (int i = 0; i + 1 < blocks.size(); i++) {
                    if (blocks.get(i).fowardPointers().isEmpty()) {
                        continue;
                    }
                    var actual = blocks.get(i).fowardPointers().getFirst();
                    var expected = blocks.get(i+1).docIds().getLast();
                    System.out.println(actual + " vs " + expected);
                    assertEquals(actual, expected);
                }
            }
        }
    }


    @Test
    public void testTenBlocksReadOffset() throws IOException {
        long pos1;

        long[] readKeys = LongStream.range(-2, (SkipListConstants.MAX_RECORDS_PER_BLOCK-32)*10).toArray();
        long[] readVals = LongStream.range(-2, (SkipListConstants.MAX_RECORDS_PER_BLOCK-32)*10).map(v -> -v).toArray();

        long[] expectedKeys = LongStream.range(0, (SkipListConstants.MAX_RECORDS_PER_BLOCK-32)*10).toArray();
        try (var writer = new SkipListWriter(docsFile)) {
            pos1 = writer.writeList(createArray(readKeys, readVals), 4, expectedKeys.length);
        }

        System.out.println(pos1);

        try (var arr = LongArrayFactory.mmapForReadingConfined(docsFile)) {
            LongArrayList allDocIds = new LongArrayList();
            LongArrayList allValues = new LongArrayList();

            var blocks = SkipListReader.parseBlocks(arr.getMemorySegment(), 0);

            for (var block : blocks) {
                System.out.println(block);
            }

            assertEquals(10, blocks.size());

            for (var block : blocks) {
                allDocIds.addAll(block.docIds());
            }

            LongList expectedAllDocIds = new LongArrayList(expectedKeys);
            LongList expectedAllValues = new LongArrayList();

            Assertions.assertEquals(expectedAllDocIds, allDocIds);
            Assertions.assertEquals(expectedAllValues, allValues);

            for (int i = 0; i < blocks.size(); i++) {
                SkipListReader.RecordView block = blocks.get(i);
                for (int fci = 0; fci < block.fc(); fci++) {
                    int skipOffset = SkipListConstants.skipOffsetForPointer(fci);
                    Assertions.assertTrue(i + skipOffset < blocks.size());
                    Assertions.assertEquals(block.fowardPointers().getLong(fci), blocks.get(i+skipOffset).highestDocId());
                }
            }
        }

    }
    @Test
    public void testSkipOffsetForPointer() {
        for (int i = 0; i < 64; i++) {
            System.out.println(i + ":" + SkipListConstants.skipOffsetForPointer(i));
        }
    }
    @Test
    public void testNumPointersForBlock() {
        for (int i = 1; i < 64; i++) {
            System.out.println(i + ":" + SkipListConstants.numPointersForBlock(i));
        }
    }

    @Test
    public void testNonRootBlockCapacity() {
        for (int i = 1; i < 64; i++) {
            System.out.println(i + ":" + SkipListConstants.nonRootBlockCapacity(i));
        }
    }

    @Test
    public void testEstimateNumBlocks() {
        for (int i = 1; i < 1024; i++) {
            System.out.println(i + ":" + SkipListConstants.estimateNumBlocks(i));
        }
    }

    @Test
    public void testNumPointersForRootBlock() {
        for (int i = 1; i < 1024; i++) {
            System.out.println(i + ":" + SkipListConstants.estimateNumBlocks(i) + ":" + SkipListConstants.numPointersForRootBlock(i));
        }
    }

    @Test
    public void calculateNumBlocks() {
        for (int i = 1; i < 1024; i++) {
            System.out.println(i + ":" + SkipListWriter.calculateActualNumBlocks(2048, i) + ":" + SkipListConstants.estimateNumBlocks(i));
        }
    }

    @Test
    public void calculateNumBlocks2() {
        System.out.println(SkipListWriter.calculateActualNumBlocks(2048,1));
    }
}
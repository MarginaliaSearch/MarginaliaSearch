package nu.marginalia.skiplist;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.array.LongArray;

import java.io.IOException;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static nu.marginalia.skiplist.SkipListConstants.*;

public class SkipListWriter implements AutoCloseable {
    private final FileChannel documentsChannel;
    private final FileChannel valuesChannel;

    private final ByteBuffer docsBuffer = ByteBuffer.allocateDirect(BLOCK_SIZE).order(ByteOrder.nativeOrder());
    private final ByteBuffer valuesBuffer = ByteBuffer.allocateDirect(VALUE_BLOCK_SIZE).order(ByteOrder.nativeOrder());

    private final LongArrayList maxValuesList = new LongArrayList();

    private long valueBlockOffset;


    public SkipListWriter(Path dataFileName, Path valuesFileName) throws IOException {
        documentsChannel = (FileChannel) Files.newByteChannel(dataFileName, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        documentsChannel.position(documentsChannel.size());

        valuesChannel = (FileChannel) Files.newByteChannel(valuesFileName, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        valuesChannel.position(documentsChannel.size());
        valueBlockOffset = valuesChannel.position();
    }

    @Override
    public void close() throws IOException {

        // Write remaining values

        valuesBuffer.flip();

        while (valuesBuffer.hasRemaining()) {
            valuesChannel.write(valuesBuffer);
        }

        int blockRemaining = (int) (VALUE_BLOCK_SIZE - (valuesChannel.position() & (VALUE_BLOCK_SIZE - 1)));
        valuesBuffer.position(0);
        valuesBuffer.limit(blockRemaining);
        while (valuesBuffer.hasRemaining()) {
            valuesChannel.write(valuesBuffer);
        }


        valuesChannel.force(false);
        valuesChannel.close();

        // Pad docs file to block size alignment

        blockRemaining = (int) (BLOCK_SIZE - (documentsChannel.position() & (BLOCK_SIZE - 1)));
        docsBuffer.position(0);
        docsBuffer.limit(blockRemaining);
        while (docsBuffer.hasRemaining()) {
            documentsChannel.write(docsBuffer);
        }

        documentsChannel.force(false);
        if ((documentsChannel.position() & (BLOCK_SIZE-1)) != 0) {
            throw new IllegalStateException("Wrote a documents file that was not aligned with block size " + BLOCK_SIZE);
        }
        documentsChannel.close();
    }

    public static void writeFooter(Path documentsFileName, String magicWord) throws IOException {

        ByteBuffer buffer = ByteBuffer.allocateDirect(BLOCK_SIZE).order(ByteOrder.nativeOrder());

        // Write a header that is compatible with the other block types so that we can diagnose
        // reader errors easier.  Actual footer goes at the end of the block though, to be able to
        // parse it regardless of block size.

        buffer.putInt(0);
        buffer.put((byte) 0);
        buffer.put((byte) FLAG_FOOTER_BLOCK);

        byte[] magicWordBytes = magicWord.getBytes(StandardCharsets.UTF_8);

        int trailerPosition = BLOCK_SIZE
                - magicWord.getBytes(StandardCharsets.UTF_8).length
                - 8; // trailer

        buffer.position(trailerPosition);
        if (magicWordBytes.length >= buffer.remaining()) {
            throw new IllegalArgumentException("Magic word string is too long");
        }

        buffer.put(magicWordBytes);

        buffer.put((byte) 0);    // reserved for future use
        buffer.put((byte) 0);    // reserved for future use
        buffer.put((byte) 0);    // reserved for future use

        buffer.put((byte) magicWordBytes.length);
        buffer.putInt(BLOCK_SIZE);

        buffer.position(0);
        buffer.limit(BLOCK_SIZE);

        try (var documentsChannel = (FileChannel) Files.newByteChannel(documentsFileName, StandardOpenOption.WRITE))
        {
            documentsChannel.position(documentsChannel.size());
            while (buffer.hasRemaining()) {
                documentsChannel.write(buffer);
            }
        }
    }

    public static void validateFooter(Path documentsFileName, String expectedMagicWord) throws IOException {

        ByteBuffer buffer = ByteBuffer.allocateDirect(BLOCK_SIZE).order(ByteOrder.nativeOrder());

        try (var documentsChannel = (FileChannel) Files.newByteChannel(documentsFileName, StandardOpenOption.READ))
        {
            documentsChannel.position(documentsChannel.size() - BLOCK_SIZE);
            while (buffer.hasRemaining()) {
                documentsChannel.read(buffer);
            }
        }
        buffer.flip();

        byte[] expectedMagicWordBytes = expectedMagicWord.getBytes(StandardCharsets.UTF_8);
        byte[] actualMagicWordBytes = new byte[expectedMagicWord.length()];

        if (expectedMagicWordBytes.length >= buffer.remaining()) {
            throw new IllegalArgumentException("Magic word string is too long");
        }

        int trailerPosition = BLOCK_SIZE
                - expectedMagicWord.getBytes(StandardCharsets.UTF_8).length
                - 8; // trailer

        buffer.position(trailerPosition);

        buffer.get(actualMagicWordBytes);

        // reserved space
        buffer.get();
        buffer.get();
        buffer.get();

        int magicStringLength = buffer.get();
        int blockSize = buffer.getInt();
        assert buffer.position() == BLOCK_SIZE;

        if (!Arrays.equals(expectedMagicWordBytes, actualMagicWordBytes) || magicStringLength != expectedMagicWord.length()) throw new IllegalArgumentException("Invalid skip list footer, mismatching magic word bytes: + " + Arrays.toString(actualMagicWordBytes));
        if (blockSize != BLOCK_SIZE) throw new IllegalArgumentException("Incompatible skip list, block size mismatch: " + blockSize + ", expected " + BLOCK_SIZE);
    }


    public void padDocuments(int nBytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(nBytes);
        buffer.order(ByteOrder.nativeOrder());
        while (buffer.hasRemaining()) {
            buffer.put((byte) 0);
        }
        buffer.flip();
        while (buffer.hasRemaining()) {
            documentsChannel.write(buffer);
        }
    }


    private void writeCompactBlockHeader(ByteBuffer buffer, int nItems, byte fc, byte flags) {
        assert nItems >= 0;
        assert nItems <= MAX_RECORDS_PER_BLOCK;
        assert fc >= 0;

        buffer.putInt(nItems);
        buffer.put(fc); // number of records
        buffer.put(flags); // forward count = 0
        buffer.putShort((short) 0);

        long valueBufferPosition = valueBlockOffset + valuesBuffer.position();
        assert (valueBufferPosition & 15) == 0;

        buffer.putLong(valueBufferPosition);

        assert (buffer.position() % 8) == 0;
    }

    private void copyValues(LongArray input, long inputOffset, int n) throws IOException {

        for (int i = 0; i < n; i++) {
            if (valuesBuffer.remaining() < 8*(RECORD_SIZE-1)) {
                valuesBuffer.flip();
                while (valuesBuffer.hasRemaining()) {
                    int wb = valuesChannel.write(valuesBuffer);
                    if (wb > 0)
                        valueBlockOffset += wb;
                }
                valuesBuffer.clear();
            }

            long valuePairOffset = inputOffset + (long) RECORD_SIZE * i;
            for (int j = 1; j < RECORD_SIZE; j++) {
                valuesBuffer.putLong(input.get(valuePairOffset + j));
            }
        }
    }

    public long writeList(LongArray input, long inputOffset, int n) throws IOException {
        long startPos = documentsChannel.position();
        assert (startPos % 8) == 0 : "Not long aligned?!" + startPos;
        assert input.isSortedN(RECORD_SIZE, inputOffset, inputOffset + RECORD_SIZE*n) : ("Not sorted @ " + LongStream.range(inputOffset, inputOffset+n*RECORD_SIZE).map(input::get).mapToObj(Long::toString).collect(Collectors.joining(", ")));
        maxValuesList.clear();


        int blockRemaining = (int) (BLOCK_SIZE - (startPos % BLOCK_SIZE));

        if (blockRemaining >= (DATA_BLOCK_HEADER_SIZE + n * ValueLayout.JAVA_LONG.byteSize())) {
            docsBuffer.clear();

            /** THE ENTIRE DATA FITS IN THE CURRENT BLOCK */

            writeCompactBlockHeader(docsBuffer, n, (byte) 0, (byte) (FLAG_END_BLOCK | FLAG_COMPACT_BLOCK));

            // Write the keys
            for (int i = 0; i < n; i++) {
                docsBuffer.putLong(input.get(inputOffset + RECORD_SIZE * i));
            }

            copyValues(input, inputOffset, n);

            docsBuffer.flip();

            while (docsBuffer.hasRemaining()) {
                documentsChannel.write(docsBuffer);
            }

            return startPos;
        }


        if (blockRemaining < Math.min(1024, BLOCK_SIZE / 2)) {
            // Add padding if we cannot reclaim the remaining parts of this block

            docsBuffer.clear();

            /** REMAINING BLOCK TOO SMALL TO RECLAIM - INSERT PADDING */
            for (int i = 0; i < blockRemaining; i++) {
                docsBuffer.put((byte) 0);
            }
            docsBuffer.flip();
            while (docsBuffer.hasRemaining()) {
                startPos += documentsChannel.write(docsBuffer);
            }
            blockRemaining = BLOCK_SIZE;
            docsBuffer.clear();
        }

        int writtenRecords = 0;
        int numBlocks = calculateActualNumBlocks(blockRemaining, n);

        {
            docsBuffer.clear();

            int rootBlockCapacity = rootBlockCapacity(blockRemaining, n);
            int rootBlockPointerCount = numPointersForRootBlock(blockRemaining, n);

            /** WRITE THE ROOT BLOCK **/

            boolean isLastBlock = numBlocks == 1;

            byte flags = 0;

            if (isLastBlock) flags |= FLAG_END_BLOCK;

            writeCompactBlockHeader(docsBuffer, rootBlockCapacity, (byte) rootBlockPointerCount, flags);

            findBlockHighestValues(input, maxValuesList,
                    inputOffset + (long) RECORD_SIZE * rootBlockCapacity,
                    numBlocks,
                    n - rootBlockCapacity);

            // Write skip pointers
            for (int pi = 0; pi < rootBlockPointerCount; pi++) {
                int skipBlocks = skipOffsetForPointer(pi);

                assert skipBlocks < 1 + numBlocks; // should be ~ 1/2 numBlocks at most for the root block

                docsBuffer.putLong(maxValuesList.getLong(skipBlocks));
            }

            // Write the keys
            for (int i = 0; i < rootBlockCapacity; i++) {
                long docId = input.get(inputOffset + RECORD_SIZE * i);
                docsBuffer.putLong(docId);
            }

            // Write the values
            copyValues(input, inputOffset, rootBlockCapacity);

            // Move offset to next block's data
            inputOffset += RECORD_SIZE * rootBlockCapacity;
            writtenRecords += rootBlockCapacity;

            // Align block with page size
            if (!isLastBlock) {
                while (docsBuffer.position() < blockRemaining)
                    docsBuffer.putLong(0L);
            }

            docsBuffer.flip();

            while (docsBuffer.hasRemaining()) {
                documentsChannel.write(docsBuffer);
            }
        }

        /** WRITE REMAINING BLOCKS **/

        for (int blockIdx = 1; blockIdx < numBlocks; blockIdx++) {
            docsBuffer.clear();

            int nRemaining = n - writtenRecords;
            int blockCapacity = nonRootBlockCapacity(blockIdx);

            int maxPointers = numPointersForBlock(blockIdx);
            int forwardPointers;
            for (forwardPointers = 0; forwardPointers < maxPointers; forwardPointers++) {
                if (blockIdx + skipOffsetForPointer(forwardPointers) + 1 >= maxValuesList.size())
                    break;
            }

            boolean isLastBlock = blockIdx == (numBlocks - 1);

            int blockSize = Math.min(nRemaining, blockCapacity);

            byte flags = 0;

            if (isLastBlock) flags |= FLAG_END_BLOCK;

            writeCompactBlockHeader(docsBuffer, blockSize, (byte) forwardPointers, flags);

            for (int pi = 0; pi < forwardPointers; pi++) {
                docsBuffer.putLong(maxValuesList.getLong(blockIdx + skipOffsetForPointer(pi)));
            }

            // Write the keys
            for (int i = 0; i < blockSize; i++) {
                long docId = input.get(inputOffset + RECORD_SIZE * i);
                docsBuffer.putLong(docId);
            }

            // Write the values
            copyValues(input, inputOffset, blockSize);

            // Move offset to next block's data
            inputOffset += (long) RECORD_SIZE * blockSize;
            writtenRecords += blockSize;

            // Align block with page size everywhere but the last, unless this is a compact block
            if (!isLastBlock) {
                while (docsBuffer.position() < docsBuffer.capacity())
                    docsBuffer.putLong(0L);
            }

            docsBuffer.flip();

            while (docsBuffer.hasRemaining()) {
                documentsChannel.write(docsBuffer);
            }
        }

        return startPos;
    }

    private void findBlockHighestValues(LongArray input,
                               LongArrayList output,
                               long offsetStart,
                               int numBlocks,
                               int n)
    {
        output.clear();

        output.add(-1); // Add a dummy value for the root block

        for (int i = 1; i < numBlocks; i++) {
            assert n >= 0;

            int blockCapacity = nonRootBlockCapacity(i);
            long offsetEnd =  offsetStart + RECORD_SIZE * Math.min(n, blockCapacity) - RECORD_SIZE;
            offsetStart += RECORD_SIZE * Math.min(n, blockCapacity);

            n -= blockCapacity;
            output.add(input.get(offsetEnd));
        }
    }


    static int calculateActualNumBlocks(int rootBlockSize, int n) {
        assert n >= 1;

        int blocks = 1; // We always generate a root block
        n-=rootBlockCapacity(rootBlockSize, n);

        for (int i = 1; n > 0; i++) {
            n-= nonRootBlockCapacity(i);
            blocks++;
        }

        return blocks;
    }

}
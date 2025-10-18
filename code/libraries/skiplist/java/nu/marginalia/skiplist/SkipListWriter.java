package nu.marginalia.skiplist;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.array.LongArray;

import java.io.IOException;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static nu.marginalia.skiplist.SkipListConstants.*;

public class SkipListWriter implements AutoCloseable {
    private final FileChannel documentsChannel;

    private final ByteBuffer docsBuffer = ByteBuffer.allocateDirect(BLOCK_SIZE).order(ByteOrder.nativeOrder());
    private final ByteBuffer valuesBuffer = ByteBuffer.allocateDirect(BLOCK_SIZE * (RECORD_SIZE-1)).order(ByteOrder.nativeOrder());

    private final LongArrayList maxValuesList = new LongArrayList();

    public SkipListWriter(Path documentsFileName) throws IOException {
        documentsChannel = (FileChannel) Files.newByteChannel(documentsFileName, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        documentsChannel.position(documentsChannel.size());
    }

    @Override
    public void close() throws IOException {
        int blockRemaining = (int) (BLOCK_SIZE - (documentsChannel.position() & (BLOCK_SIZE - 1)));
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

    public long documentsPosition() throws IOException {
        return documentsChannel.position();
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

        assert (buffer.position() % 8) == 0;
    }

    private void writeValueBlockHeader(ByteBuffer buffer, int nItems, byte flags) {
        assert nItems >= 0;
        assert nItems <= (BLOCK_SIZE - VALUE_BLOCK_HEADER_SIZE) / (8 * (RECORD_SIZE-1));

        buffer.putInt(nItems);
        buffer.put((byte) 0); // space reserved for fc
        buffer.put((byte) flags);
        buffer.putShort((short) 0);

        assert (buffer.position() % 8) == 0;
    }

    private void copyValues(ByteBuffer dest, LongArray input, long inputOffset, int n) {

        int itemsPerBlock = (BLOCK_SIZE - VALUE_BLOCK_HEADER_SIZE) / (8 * (RECORD_SIZE-1));
        int itemsCopied = 0;

        while (n > 0) {
            int itemsInBlock = Math.min(n, itemsPerBlock);

            int flags = FLAG_VALUE_BLOCK;
            boolean atEnd;
            if ((atEnd = (itemsInBlock == n)))
                flags |= FLAG_END_BLOCK;

            writeValueBlockHeader(valuesBuffer, itemsInBlock, (byte) flags);

            for (int i = 0; i < itemsInBlock; i++) {
                for (int j = 1; j < RECORD_SIZE; j++) {
                    valuesBuffer.putLong(input.get(inputOffset + RECORD_SIZE * (i + itemsCopied) + j));
                }
            }

            if (!atEnd) {
                // pad to block boundary for non-terminal blocks

                while ((valuesBuffer.position() & (BLOCK_SIZE-1)) != 0) {
                    valuesBuffer.putLong(0L);
                }
            }

            itemsCopied += itemsInBlock;
            n -= itemsInBlock;
        }

    }

    public long writeList(LongArray input, long inputOffset, int n) throws IOException {
        long startPos = documentsChannel.position();
        assert (startPos % 8) == 0 : "Not long aligned?!" + startPos;
        assert input.isSortedN(RECORD_SIZE, inputOffset, inputOffset + RECORD_SIZE*n) : ("Not sorted @ " + LongStream.range(inputOffset, inputOffset+n*RECORD_SIZE).map(input::get).mapToObj(Long::toString).collect(Collectors.joining(", ")));
        maxValuesList.clear();


        int blockRemaining = (int) (BLOCK_SIZE - (startPos % BLOCK_SIZE));

        if (blockRemaining >= (DATA_BLOCK_HEADER_SIZE + RECORD_SIZE * n * ValueLayout.JAVA_LONG.byteSize() + VALUE_BLOCK_HEADER_SIZE)) {
            docsBuffer.clear();
            valuesBuffer.clear();

            /** THE ENTIRE DATA FITS IN THE CURRENT BLOCK */

            writeCompactBlockHeader(docsBuffer, n, (byte) 0, (byte) (FLAG_END_BLOCK | FLAG_COMPACT_BLOCK));

            // Write the keys
            for (int i = 0; i < n; i++) {
                docsBuffer.putLong(input.get(inputOffset + RECORD_SIZE * i));
            }

            copyValues(valuesBuffer, input, inputOffset, n);

            docsBuffer.flip();
            valuesBuffer.flip();

            while (docsBuffer.hasRemaining() || valuesBuffer.hasRemaining()) {
                documentsChannel.write(new ByteBuffer[] { docsBuffer, valuesBuffer });
            }

            return startPos;
        }

        if (blockRemaining < SkipListConstants.MIN_TRUNCATED_BLOCK_SIZE) {
            docsBuffer.clear();
            valuesBuffer.clear();

            /** REMAINING BLOCK TOO SMALL TO RECLAIM - INSERT PADDING */
            for (int i = 0; i < blockRemaining; i++) {
                docsBuffer.put((byte) 0);
            }
            docsBuffer.flip();
            while (docsBuffer.hasRemaining()) {
                startPos += documentsChannel.write(docsBuffer);
            }
            blockRemaining = BLOCK_SIZE;
        }

        int writtenRecords = 0;
        int numBlocks = calculateActualNumBlocks(blockRemaining, n);

        {
            docsBuffer.clear();
            valuesBuffer.clear();

            int rootBlockCapacity = rootBlockCapacity(blockRemaining, n);
            int rootBlockPointerCount = numPointersForRootBlock(blockRemaining, n);

            /** WRITE THE ROOT BLOCK **/

            boolean isLastBlock = numBlocks == 1;
            boolean isCompactBlock = isLastBlock && canGenerateCompactBlock(n, rootBlockCapacity);

            byte flags = 0;

            if (isLastBlock) flags |= FLAG_END_BLOCK;
            if (isCompactBlock) flags |= FLAG_COMPACT_BLOCK;

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
            copyValues(valuesBuffer, input, inputOffset, rootBlockCapacity);

            // Move offset to next block's data
            inputOffset += RECORD_SIZE * rootBlockCapacity;
            writtenRecords += rootBlockCapacity;

            while (docsBuffer.position() < blockRemaining)
                docsBuffer.putLong(0L);

            // Align block with page size
            if (!isLastBlock) {
                while (valuesBuffer.position() < valuesBuffer.capacity())
                    valuesBuffer.putLong(0L);
            }

            docsBuffer.flip();
            valuesBuffer.flip();

            while (docsBuffer.hasRemaining() || valuesBuffer.hasRemaining()) {
                documentsChannel.write(new ByteBuffer[] { docsBuffer, valuesBuffer });
            }
        }

        /** WRITE REMAINING BLOCKS **/

        for (int blockIdx = 1; blockIdx < numBlocks; blockIdx++) {
            docsBuffer.clear();
            valuesBuffer.clear();

            int nRemaining = n - writtenRecords;
            int blockCapacity = nonRootBlockCapacity(blockIdx);

            int maxPointers = numPointersForBlock(blockIdx);
            int forwardPointers;
            for (forwardPointers = 0; forwardPointers < maxPointers; forwardPointers++) {
                if (blockIdx + skipOffsetForPointer(forwardPointers) + 1 >= maxValuesList.size())
                    break;
            }

            boolean isLastBlock = blockIdx == (numBlocks - 1);
            boolean isCompactBlock = isLastBlock && canGenerateCompactBlock(nRemaining, blockCapacity);

            int blockSize = Math.min(nRemaining, blockCapacity);

            byte flags = 0;

            if (isLastBlock) flags |= FLAG_END_BLOCK;
            if (isCompactBlock) flags |= FLAG_COMPACT_BLOCK;

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
            copyValues(valuesBuffer, input, inputOffset, blockSize);

            // Move offset to next block's data
            inputOffset += RECORD_SIZE * blockSize;
            writtenRecords += blockSize;

            // Align block with page size everywhere but the last, unless this is a compact block
            if (!isLastBlock || !isCompactBlock) {
                while (docsBuffer.position() < docsBuffer.capacity())
                    docsBuffer.putLong(0L);
                while (valuesBuffer.position() < valuesBuffer.capacity())
                    valuesBuffer.putLong(0L);
            }

            docsBuffer.flip();
            valuesBuffer.flip();

            while (docsBuffer.hasRemaining() || valuesBuffer.hasRemaining()) {
                documentsChannel.write(new ByteBuffer[] { docsBuffer, valuesBuffer });
            }
        }

        return startPos;
    }

    private boolean canGenerateCompactBlock(int nItems, int blockCapacity) {
        long spareCapacity = blockCapacity - nItems;

        return spareCapacity > (RECORD_SIZE-1)*nItems + VALUE_BLOCK_HEADER_SIZE/8;
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
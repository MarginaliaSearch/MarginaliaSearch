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

import static nu.marginalia.skiplist.SkipListConstants.*;

public class SkipListWriter implements AutoCloseable {
    private final FileChannel documentsChannel;

    private final ByteBuffer docsBuffer = ByteBuffer.allocateDirect(BLOCK_SIZE).order(ByteOrder.nativeOrder());
    private final LongArrayList maxValuesList = new LongArrayList();

    public SkipListWriter(Path documentsFileName) throws IOException {
        this.documentsChannel = (FileChannel) Files.newByteChannel(documentsFileName, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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

    public long writeList(LongArray input, long offset, int n) throws IOException {
        long startPos = documentsChannel.position();
        assert (startPos % 8) == 0 : "Not long aligned?!" + startPos;
        assert input.isSortedN(2, offset, offset + 2L*n) : "Not sorted @ " + input.hashCode();
        maxValuesList.clear();

        int blockRemaining = (int) (BLOCK_SIZE - (startPos % BLOCK_SIZE));

        if (blockRemaining >= (HEADER_SIZE + RECORD_SIZE * n * ValueLayout.JAVA_LONG.byteSize())) {
            /** THE ENTIRE DATA FITS IN THE CURRENT BLOCK */

            docsBuffer.clear();

            writeCompactBlockHeader(docsBuffer, n, (byte) 0, FLAG_END_BLOCK);

            // Write the keys
            for (int i = 0; i < n; i++) {
                docsBuffer.putLong(input.get(offset + 2L * i));
            }

            // Write the values
            for (int i = 0; i < n; i++) {
                docsBuffer.putLong(input.get(offset + 2L * i + 1));
            }

            docsBuffer.flip();
            while (docsBuffer.hasRemaining()) {
                documentsChannel.write(docsBuffer);
            }

            return startPos;
        }

        if (blockRemaining < SkipListConstants.MIN_TRUNCATED_BLOCK_SIZE) {

            /** REMAINING BLOCK TOO SMALL TO RECLAIM - INSERT PADDING */
            docsBuffer.clear();
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
            int rootBlockCapacity = rootBlockCapacity(blockRemaining, n);
            int rootBlockPointerCount = numPointersForRootBlock(blockRemaining, n);

            /** WRITE THE ROOT BLOCK **/

            docsBuffer.clear();
            byte flags = 0;
            if (numBlocks == 1) {
                flags = FLAG_END_BLOCK;
            }

            writeCompactBlockHeader(docsBuffer, rootBlockCapacity, (byte) rootBlockPointerCount, flags);

            findBlockHighestValues(input, maxValuesList,
                    offset + (long) RECORD_SIZE * rootBlockCapacity,
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
                docsBuffer.putLong(input.get(offset + 2L * i));
            }

            // Write the values
            for (int i = 0; i < rootBlockCapacity; i++) {
                docsBuffer.putLong(input.get(offset + 2L * i + 1));
            }

            // Move offset to next block's data
            offset += 2L * rootBlockCapacity;
            writtenRecords += rootBlockCapacity;

            // Align block with page size
            if (numBlocks > 1) {
                while (docsBuffer.position() < blockRemaining) {
                    docsBuffer.putLong(0L);
                }
            }

            docsBuffer.flip();
            while (docsBuffer.hasRemaining()) {
                documentsChannel.write(docsBuffer);
            }
        }

        /** WRITE REMAINING BLOCKS **/

        for (int blockIdx = 1; blockIdx < numBlocks; blockIdx++) {
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
            docsBuffer.clear();

            byte flags = 0;
            if (isLastBlock) {
                flags = FLAG_END_BLOCK;
            }
            writeCompactBlockHeader(docsBuffer, blockSize, (byte) forwardPointers, flags);

            for (int pi = 0; pi < forwardPointers; pi++) {
                docsBuffer.putLong(maxValuesList.getLong(blockIdx + skipOffsetForPointer(pi)));
            }

            // Write the keys
            for (int i = 0; i < blockSize; i++) {
                long docId = input.get(offset + 2L * i);
                docsBuffer.putLong(docId);
            }

            // Write the values
            for (int i = 0; i < blockSize; i++) {
                long val = input.get(offset + 2L * i + 1);
                docsBuffer.putLong(val);
            }

            // Move offset to next block's data
            offset += 2L * Math.min(nRemaining, blockCapacity);
            writtenRecords += Math.min(nRemaining, blockCapacity);

            // Align block with page size everywhere but the last
            if (!isLastBlock) {
                while (docsBuffer.position() < docsBuffer.capacity()) {
                    docsBuffer.putLong(0L);
                }
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
            long offsetEnd =  offsetStart + 2L*Math.min(n, blockCapacity) - 2L;
            offsetStart += 2L*Math.min(n, blockCapacity);

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
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
    private final FileChannel outputChannel;


    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BLOCK_SIZE).order(ByteOrder.nativeOrder());
    private final LongArrayList maxValuesList = new LongArrayList();

    public SkipListWriter(Path fileName) throws IOException {
        this.outputChannel = (FileChannel) Files.newByteChannel(fileName, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public void close() throws IOException {
        outputChannel.force(false);
        outputChannel.close();
    }

    public long position() throws IOException {
        return outputChannel.position();
    }

    public void pad(int nBytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(nBytes);
        buffer.order(ByteOrder.nativeOrder());
        while (buffer.hasRemaining()) {
            buffer.put((byte) 0);
        }
        buffer.flip();
        while (buffer.hasRemaining()) {
            outputChannel.write(buffer);
        }
    }

    public long writeList(LongArray input, long offset, int n) throws IOException {
        long startPos = outputChannel.position();
        assert (startPos % 8) == 0 : "Not long aligned?!" + startPos;
        assert input.isSortedN(2, offset, offset + 2L*n) : "Not sorted @ " + input.hashCode();
        maxValuesList.clear();

        int blockRemaining = (int) (BLOCK_SIZE - (startPos % BLOCK_SIZE));

        if (blockRemaining >= (HEADER_SIZE + RECORD_SIZE * n * ValueLayout.JAVA_LONG.byteSize())) {
            /** THE ENTIRE DATA FITS IN THE CURRENT BLOCK */

            buffer.clear();
            buffer.put((byte) n); // number of records
            buffer.put((byte) 0); // forward count = 0
            buffer.put(FLAG_END_BLOCK); // this is the last block
            // pad to 8 byte alignment
            buffer.put((byte) 0);
            buffer.putInt(n);

            // Write the keys
            for (int i = 0; i < n; i++) {
                buffer.putLong(input.get(offset + 2L * i));
            }

            // Write the values
            for (int i = 0; i < n; i++) {
                buffer.putLong(input.get(offset + 2L * i + 1));
            }

            buffer.flip();
            while (buffer.hasRemaining()) {
                outputChannel.write(buffer);
            }

            return startPos;
        }

        if (blockRemaining < BLOCK_SIZE / 2) {

            /** REMAINING BLOCK TOO SMALL TO RECLAIM - INSERT PADDING */
            buffer.clear();
            for (int i = 0; i < blockRemaining; i++) {
                buffer.put((byte) 0);
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                startPos += outputChannel.write(buffer);
            }
            blockRemaining = BLOCK_SIZE;
        }

        int writtenRecords = 0;
        int numBlocks = calculateActualNumBlocks(blockRemaining, n);

        {
            int rootBlockCapacity = rootBlockCapacity(blockRemaining, n);
            int rootBlockPointerCount = numPointersForRootBlock(n);

            /** WRITE THE ROOT BLOCK **/

            buffer.clear();
            buffer.put((byte) rootBlockCapacity); // number of records
            buffer.put((byte) rootBlockPointerCount); // forward count
            if (numBlocks == 1) {
                buffer.put((byte) FLAG_END_BLOCK); // this is the last block
            } else {
                buffer.put((byte) 0);
            }
            // pad to 8 byte alignment
            buffer.put((byte) 0);
            buffer.putInt(n);

            findBlockHighestValues(input, maxValuesList,
                    offset + (long) RECORD_SIZE * rootBlockCapacity,
                    numBlocks,
                    n - rootBlockCapacity);

            // Write skip pointers
            for (int pi = 0; pi < rootBlockPointerCount; pi++) {
                int skipBlocks = skipOffsetForPointer(pi);

                assert skipBlocks < 1 + numBlocks; // should be ~ 1/2 numBlocks at most for the root block

                buffer.putLong(maxValuesList.getLong(skipBlocks));
            }

            // Write the keys
            for (int i = 0; i < rootBlockCapacity; i++) {
                buffer.putLong(input.get(offset + 2L * i));
            }

            // Write the values
            for (int i = 0; i < rootBlockCapacity; i++) {
                buffer.putLong(input.get(offset + 2L * i + 1));
            }

            // Move offset to next block's data
            offset += 2L * rootBlockCapacity;
            writtenRecords += rootBlockCapacity;

            // Align block with page size
            if (numBlocks > 1) {
                while (buffer.position() < blockRemaining) {
                    buffer.putLong(0L);
                }
            }

            buffer.flip();
            while (buffer.hasRemaining()) {
                outputChannel.write(buffer);
            }
        }

        /** WRITE REMAINING BLOCKS **/

        for (int blockIdx = 1; blockIdx < numBlocks; blockIdx++) {
            int nRemaining = n - writtenRecords;
            int blockCapacity = nonRootBlockCapacity(blockIdx);

            int forwardPointers = numPointersForBlock(blockIdx);
            while (forwardPointers > 0 && blockIdx + skipOffsetForPointer(forwardPointers) >= numBlocks) {
                forwardPointers--;
            }

            boolean isLastBlock = blockIdx == (numBlocks - 1);
            int blockSize = Math.min(nRemaining, blockCapacity);
            buffer.clear();
            buffer.put((byte) blockSize); // number of records
            buffer.put((byte) forwardPointers); // forward count
            if (isLastBlock) {
                buffer.put((byte) FLAG_END_BLOCK); // this is the last block
            }
            else {
                buffer.put((byte) 0);
            }
            // pad to 8 byte alignment
            buffer.put((byte) 0);
            buffer.putInt(nRemaining);

            for (int pi = 0; pi < forwardPointers; pi++) {
                int skipBlocks = skipOffsetForPointer(pi);

                assert skipBlocks < 1 + numBlocks; // should be ~ 1/2 numBlocks at most for the root block

                buffer.putLong(maxValuesList.getLong(blockIdx + skipBlocks));
            }

            // Write the keys
            for (int i = 0; i < blockSize; i++) {
                long docId = input.get(offset + 2L * i);
                buffer.putLong(docId);
            }

            // Write the values
            for (int i = 0; i < blockSize; i++) {
                long val = input.get(offset + 2L * i + 1);
                buffer.putLong(val);
            }

            // Move offset to next block's data
            offset += 2L * Math.min(nRemaining, blockCapacity);
            writtenRecords += Math.min(nRemaining, blockCapacity);

            // Align block with page size everywhere but the last
            if (!isLastBlock) {
                while (buffer.position() < buffer.capacity()) {
                    buffer.putLong(0L);
                }
            }

            buffer.flip();
            while (buffer.hasRemaining()) {
                outputChannel.write(buffer);
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

    static int skipOffsetForPointer(int pointerIdx) {
        return (1 << (pointerIdx));
    }

    static int numPointersForBlock(int blockIdx) {
        assert blockIdx >= 1;
        return Integer.numberOfTrailingZeros(blockIdx);
    }

    static int numPointersForRootBlock(int n) {
        return Integer.numberOfTrailingZeros(Integer.highestOneBit(estimateNumBlocks(n)));
    }

    static int rootBlockCapacity(int rootBlockSize, int n) {
        return Math.min(n, (rootBlockSize - HEADER_SIZE - 8 * numPointersForRootBlock(n)) / (RECORD_SIZE * 8));
    }

    static int nonRootBlockCapacity(int blockIdx) {
        assert blockIdx >= 1;
        return (BLOCK_SIZE - HEADER_SIZE - 8 * numPointersForBlock(blockIdx)) / (RECORD_SIZE * 8);
    }

    static int estimateNumBlocks(int n) {
        return n / 31 + Integer.signum(n % 31);
    }


}
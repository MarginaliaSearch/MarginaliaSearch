package nu.marginalia.index.reverse.construction.prio;

import nu.marginalia.array.algo.LongArrayTransformations;
import nu.marginalia.skiplist.compression.DocIdCompressor;
import nu.marginalia.skiplist.compression.input.ArrayCompressorInput;
import nu.marginalia.skiplist.compression.output.ByteBufferCompressorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/** Constructs document ids list for the priority reverse index using vbyte compression. */
public class PrioDocIdsVByteTransformer implements LongArrayTransformations.LongIOTransformer, AutoCloseable {

    private final FileChannel writeChannel;
    private final FileChannel readChannel;

    private final ByteBuffer readBuffer = ByteBuffer.allocate(65536).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(65536).order(ByteOrder.nativeOrder());

    long startL = 0;
    long writeOffsetB = 0;

    public PrioDocIdsVByteTransformer(FileChannel writeChannel, FileChannel readChannel) {
        this.writeChannel = writeChannel;
        this.readChannel = readChannel;
    }

    @Override
    public long transform(long pos, long endL) throws IOException {
        int sizeL = (int) (endL - startL);
        if (sizeL == 0) {
            throw new IllegalStateException("Empty range");
        }

        // Read all doc IDs for this segment
        long[] docIds = readDocIds(sizeL);

        // Deduplicate in place (input is already sorted)
        int distinctCount = deduplicate(docIds, sizeL);

        // Calculate start offset in the output
        long startOffsetB = writeOffsetB;

        // Write 4-byte little-endian count prefix
        writeBuffer.clear();
        writeBuffer.order(ByteOrder.LITTLE_ENDIAN);
        writeBuffer.putInt(distinctCount);
        writeBuffer.order(ByteOrder.nativeOrder());

        // Compress using DocIdCompressor
        long[] deduped = (distinctCount < sizeL) ? Arrays.copyOf(docIds, distinctCount) : docIds;
        ArrayCompressorInput input = new ArrayCompressorInput(deduped);
        ByteBufferCompressorBuffer compressorBuffer = new ByteBufferCompressorBuffer(writeBuffer);
        DocIdCompressor.compress(input, distinctCount, compressorBuffer);

        // Flush writeBuffer to channel
        writeBuffer.flip();
        while (writeBuffer.hasRemaining()) {
            int written = writeChannel.write(writeBuffer, writeOffsetB);
            writeOffsetB += written;
        }

        startL = endL;
        return startOffsetB;
    }

    private long[] readDocIds(int count) throws IOException {
        long[] result = new long[count];
        int toBeRead = count * 8;
        int idx = 0;

        readChannel.position(startL * 8);
        readBuffer.clear();

        while (toBeRead > 0) {
            readBuffer.clear();
            readBuffer.limit(Math.min(readBuffer.capacity(), toBeRead));
            int bytesRead = readChannel.read(readBuffer);
            if (bytesRead <= 0) break;
            toBeRead -= bytesRead;
            readBuffer.flip();

            while (readBuffer.hasRemaining()) {
                result[idx++] = readBuffer.getLong();
            }
        }

        return result;
    }

    private int deduplicate(long[] sorted, int length) {
        if (length <= 1) return length;

        int writePos = 1;
        for (int i = 1; i < length; i++) {
            if (sorted[i] != sorted[i - 1]) {
                sorted[writePos++] = sorted[i];
            }
        }
        return writePos;
    }

    @Override
    public void close() throws IOException {
        // Nothing to flush; each transform() call flushes its own data
    }
}

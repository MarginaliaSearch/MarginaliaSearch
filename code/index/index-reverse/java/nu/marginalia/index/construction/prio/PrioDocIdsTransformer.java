package nu.marginalia.index.construction.prio;

import nu.marginalia.array.algo.LongArrayTransformations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/** Constructs document ids list priority reverse index */
public class PrioDocIdsTransformer implements LongArrayTransformations.LongIOTransformer {
    private final FileChannel writeChannel;
    private final FileChannel readChannel;

    private final ByteBuffer buffer = ByteBuffer.allocate(8192);

    long startL = 0;
    long writeOffsetB = 0;

    public PrioDocIdsTransformer(FileChannel writeChannel,
                                 FileChannel readChannel) {
        this.writeChannel = writeChannel;
        this.readChannel = readChannel;
    }

    @Override
    public long transform(long pos, long endL) throws IOException {

        final int sizeL = (int) ((endL - startL));
        final long startOffsetB = writeOffsetB;

        if (sizeL == 0) {
            return -1;
        }

        readChannel.position(startL * 8);

        buffer.clear();
        buffer.putLong(sizeL);

        int toBeWrittenB = 8 * (1 + sizeL);
        do {
            buffer.limit(Math.min(buffer.capacity(), toBeWrittenB));
            readChannel.read(buffer);
            buffer.flip();

            while (buffer.hasRemaining()) {
                int written = writeChannel.write(buffer, writeOffsetB);
                writeOffsetB += written;
                toBeWrittenB -= written;
            }

            buffer.clear();
        } while (toBeWrittenB > 0);

        startL = endL;
        return startOffsetB;
    }
}

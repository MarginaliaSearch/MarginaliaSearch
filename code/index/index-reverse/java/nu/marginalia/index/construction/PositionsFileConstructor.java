package nu.marginalia.index.construction;

import nu.marginalia.sequence.GammaCodedSequence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PositionsFileConstructor implements AutoCloseable {
    private final Path file;
    private final FileChannel channel;

    private long offset;
    private final ByteBuffer workBuffer = ByteBuffer.allocate(8192);

    public PositionsFileConstructor(Path file) throws IOException {
        this.file = file;

        channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    /** Add a term to the positions file
     * @param termMeta the term metadata
     * @param positions the positions of the term
     * @return the offset of the term in the file
     */
    public long add(byte termMeta, GammaCodedSequence positions) throws IOException {
        synchronized (file) {
            var positionBuffer = positions.buffer();
            int size = 1 + positionBuffer.remaining();

            if (workBuffer.remaining() < size) {
                workBuffer.flip();
                channel.write(workBuffer);
                workBuffer.clear();
            }
            workBuffer.put(termMeta);
            workBuffer.put(positionBuffer);

            offset += size;
            return offset;
        }
    }

    public void close() throws IOException {
        channel.force(false);
        channel.close();
    }
}

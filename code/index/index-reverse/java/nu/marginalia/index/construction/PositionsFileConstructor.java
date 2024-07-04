package nu.marginalia.index.construction;

import nu.marginalia.index.positions.PositionCodec;
import nu.marginalia.sequence.GammaCodedSequence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** A class for constructing a positions file.  This class is thread-safe.
 *
 * <p></p>
 *
 * The positions data is concatenated in the file, with each term's metadata
 * followed by its positions.  The metadata is a single byte, and the positions
 * are encoded using the Elias Gamma code, with zero padded bits at the end to
 * get octet alignment.
 *
 * <p></p>
 *
 * It is the responsibility of the caller to keep track of the byte offset of
 * each posting in the file.
 */
public class PositionsFileConstructor implements AutoCloseable {
    private final ByteBuffer workBuffer = ByteBuffer.allocate(65536);
    
    private final Path file;
    private final FileChannel channel;

    private long offset;

    public PositionsFileConstructor(Path file) throws IOException {
        this.file = file;

        channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    /** Add a term to the positions file
     * @param termMeta the term metadata
     * @param positionsBuffer the positions of the term
     * @return the offset of the term in the file, with the size of the data in the highest byte
     */
    public long add(byte termMeta, ByteBuffer positionsBuffer) throws IOException {
        synchronized (file) {
            int size = 1 + positionsBuffer.remaining();

            if (workBuffer.remaining() < size) {
                workBuffer.flip();
                channel.write(workBuffer);
                workBuffer.clear();
            }

            workBuffer.put(termMeta);
            workBuffer.put(positionsBuffer);

            long ret = PositionCodec.encode(size, offset);

            offset += size;

            return ret;
        }
    }

    public void close() throws IOException {
        while (workBuffer.position() < workBuffer.limit()) {
            workBuffer.flip();
            channel.write(workBuffer);
        }

        channel.force(false);
        channel.close();
    }
}

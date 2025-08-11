package nu.marginalia.index.construction;

import nu.marginalia.index.positions.PositionCodec;

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
    private final int BLOCK_SIZE = 4096;
    
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

            padToAlignment(size);

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

    private void padToAlignment(int size) throws IOException {
        if (size > BLOCK_SIZE)
            return;

        // Check if the putative write starts and ends on the same block
        long currentBlock = offset & -BLOCK_SIZE;
        long endBlock = (offset + size) & -BLOCK_SIZE;
        if (currentBlock == endBlock)
            return;

        // We've already checked that size <= BLOCK_SIZE
        // so we can safely assume endBlock = the next block boundary

        int toPad = (int) (endBlock - offset);
        offset += toPad;

        int remainingCapacity = workBuffer.capacity() - workBuffer.position();

        if (remainingCapacity >= toPad) {
            workBuffer.position(workBuffer.position() + toPad);
        }
        else {
            toPad -= remainingCapacity;
            workBuffer.position(workBuffer.capacity());
            workBuffer.flip();
            channel.write(workBuffer);

            workBuffer.clear();
            workBuffer.position(toPad);
        }
    }

    public void close() throws IOException {
        if (workBuffer.hasRemaining()) {
            workBuffer.flip();

            while (workBuffer.hasRemaining())
                channel.write(workBuffer);
        }

        long remainingBlockSize = BLOCK_SIZE - (channel.position() & -BLOCK_SIZE);
        if (remainingBlockSize != 0) {
            workBuffer.position(0);
            workBuffer.limit(0);
            while (workBuffer.hasRemaining())
                channel.write(workBuffer);
        }

        channel.force(false);
        channel.close();
    }
}

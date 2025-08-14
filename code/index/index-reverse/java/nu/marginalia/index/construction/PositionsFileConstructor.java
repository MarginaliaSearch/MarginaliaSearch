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
 * are encoded varints.
 * <p></p>
 *
 * It is the responsibility of the caller to keep track of the byte offset of
 * each posting in the file.
 */
public class PositionsFileConstructor implements AutoCloseable {
    private final Path file;
    private final FileChannel channel;

    public PositionsFileConstructor(Path file) throws IOException {
        this.file = file;

        channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    /** Represents a block of positions lists.  Each writer thread should hold on to
     * a block object to ensure the locality of its positions lists.
     * When finished, commit() must be run.
     * */
    public class PositionsFileBlock {
        private final ByteBuffer workBuffer = ByteBuffer.allocate(1024*1024*16);
        private long position;

        public PositionsFileBlock(long position) {
            this.position = position;
        }

        public boolean fitsData(int size) {
            return workBuffer.remaining() >= size;
        }

        public void commit() throws IOException {
            workBuffer.position(0);
            workBuffer.limit(workBuffer.capacity());
            int pos = 0;
            while (workBuffer.hasRemaining()) {
                pos += channel.write(workBuffer, this.position + pos + workBuffer.position());
            }
        }

        private void relocate() throws IOException {
            workBuffer.clear();
            position = channel.position();
            while (workBuffer.hasRemaining()) {
                channel.write(workBuffer);
            }
            workBuffer.clear();
        }

        public long position() {
            return this.position + workBuffer.position();
        }
        public void put(byte b) {
            workBuffer.put(b);
        }
        public void put(ByteBuffer buffer) {
            workBuffer.put(buffer);
        }
    }

    public PositionsFileBlock getBlock() throws IOException {
        synchronized (this) {
            var block = new PositionsFileBlock(channel.position());
            block.relocate();
            return block;
        }
    }

    /** Add a term to the positions file
     *
     * @param block a block token to ensure data locality
     * @param termMeta the term metadata
     * @param positionsBuffer the positions of the term
     *
     * @return the offset of the term in the file, with the size of the data in the highest byte
     */
    public long add(PositionsFileBlock block, byte termMeta, ByteBuffer positionsBuffer) throws IOException {
        int size = 1 + positionsBuffer.remaining();

        if (!block.fitsData(size)) {
            synchronized (this) {
                block.commit();
                block.relocate();
            }
        }
        synchronized (file) {
            long offset = block.position();

            block.put(termMeta);
            block.put(positionsBuffer);

            return PositionCodec.encode(size, offset);
        }
    }

    public void close() throws IOException {
        channel.force(false);
        channel.close();
    }
}

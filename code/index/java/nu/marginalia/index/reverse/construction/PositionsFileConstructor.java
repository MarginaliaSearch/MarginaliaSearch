package nu.marginalia.index.reverse.construction;

import nu.marginalia.index.reverse.positions.PositionCodec;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Creates a positions run, batching payloads to avoid a write per posting. */
public class PositionsFileConstructor implements AutoCloseable {
    private final BufferedOutputStream output;
    private long filePosition;
    private boolean closed;

    public PositionsFileConstructor(Path file) throws IOException {
        output = new BufferedOutputStream(Files.newOutputStream(file,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), 1024 * 1024);
    }

    public long add(byte[] bytes, int offset, int size) throws IOException {
        checkOpen();
        if (size < 0 || size > 0xFFFF || filePosition + size >= (1L << 48)) {
            throw new IllegalArgumentException("Positions exceed the packed offset/length limits");
        }

        long encodedOffset = PositionCodec.encode(size, filePosition);
        output.write(bytes, offset, size);
        filePosition += size;
        return encodedOffset;
    }

    /** Returns the destination offset so callers can relocate posting pointers
     * without decoding their position payloads.
     */
    public long copyRange(PositionsFileReader source, long offset, long length) throws IOException {
        checkOpen();
        if (length < 0 || length >= (1L << 48) - filePosition) {
            throw new IllegalArgumentException("Positions exceed 48-bit offsets");
        }

        long destinationOffset = filePosition;
        source.copyTo(offset, length, output);
        filePosition += length;
        return destinationOffset;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Positions writer is closed");
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        output.close();
    }
}

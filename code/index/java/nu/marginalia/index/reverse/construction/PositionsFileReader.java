package nu.marginalia.index.reverse.construction;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads ahead in each merge input to avoid a file read for every posting. */
public class PositionsFileReader implements AutoCloseable {
    private final BufferedInputStream input;
    private final byte[] buffer = new byte[65536];
    private long position;

    public PositionsFileReader(Path file) throws IOException {
        input = new BufferedInputStream(Files.newInputStream(file), 1024 * 1024);
    }

    /** Source ranges must not overlap or move backwards; merges consume each input in order. */
    void copyTo(long offset, long length, OutputStream destination) throws IOException {
        if (offset < position) {
            throw new IOException("Positions run is not sequential: " + offset + " after " + position);
        }

        input.skipNBytes(offset - position);
        position = offset;

        while (length > 0) {
            int bytesRead = input.read(buffer, 0, (int) Math.min(length, buffer.length));
            if (bytesRead == -1) {
                throw new EOFException("Truncated positions run at " + position);
            }
            destination.write(buffer, 0, bytesRead);
            position += bytesRead;
            length -= bytesRead;
        }
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}

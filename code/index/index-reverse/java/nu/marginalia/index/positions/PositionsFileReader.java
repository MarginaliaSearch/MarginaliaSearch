package nu.marginalia.index.positions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PositionsFileReader implements AutoCloseable {
    private final FileChannel positions;
    private static final Logger logger = LoggerFactory.getLogger(PositionsFileReader.class);

    public PositionsFileReader(Path positionsFile) throws IOException {
        this.positions = FileChannel.open(positionsFile, StandardOpenOption.READ);
    }

    /** Get the positions for a term in the index, as pointed out by the encoded offset;
     * intermediate buffers are allocated from the provided arena allocator. */
    public TermData getTermData(Arena arena, long sizeEncodedOffset) {
        int length = PositionCodec.decodeSize(sizeEncodedOffset);
        long offset = PositionCodec.decodeOffset(sizeEncodedOffset);

        var segment = arena.allocate(length);
        var buffer = segment.asByteBuffer();

        try {
            positions.read(buffer, offset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new TermData(buffer);
    }

    @Override
    public void close() throws IOException {
        positions.close();
    }

}

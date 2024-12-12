package nu.marginalia.index.positions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PositionsFileReader implements AutoCloseable {
    private final Arena arena;
    private final MemorySegment positionsSegment;
    private static final Logger logger = LoggerFactory.getLogger(PositionsFileReader.class);

    public PositionsFileReader(Path positionsFile) throws IOException {
        arena = Arena.ofShared();

        try (var channel = FileChannel.open(positionsFile, StandardOpenOption.READ)) {
            positionsSegment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
        }
    }

    /** Get the positions for a term in the index, as pointed out by the encoded offset;
     * intermediate buffers are allocated from the provided arena allocator. */
    public TermData getTermData(Arena arena, long sizeEncodedOffset) {
        int length = PositionCodec.decodeSize(sizeEncodedOffset);
        long offset = PositionCodec.decodeOffset(sizeEncodedOffset);

        var segment = arena.allocate(length);

        MemorySegment.copy(positionsSegment, offset, segment, 0, length);

        return new TermData(segment.asByteBuffer());
    }

    @Override
    public void close() throws IOException {
        arena.close();
    }

}

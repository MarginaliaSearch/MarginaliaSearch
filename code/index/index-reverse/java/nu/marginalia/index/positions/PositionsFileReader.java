package nu.marginalia.index.positions;

import nu.marginalia.uring.UringFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads positions data from the positions file */
public class PositionsFileReader implements AutoCloseable {

    private final UringFileReader uringFileReader;
    private static final Logger logger = LoggerFactory.getLogger(PositionsFileReader.class);

    public PositionsFileReader(Path positionsFile) throws IOException {
        uringFileReader = new UringFileReader(positionsFile,  false);
    }

    @Override
    public void close() throws IOException {
        uringFileReader.close();
    }

    /** Get the positions for a keywords in the index, as pointed out by the encoded offsets;
     * intermediate buffers are allocated from the provided arena allocator. */
    public TermData[] getTermData(Arena arena, long[] offsets) {
        TermData[] ret = new TermData[offsets.length];

        int sizeTotal = 0;

        for (int i = 0; i < offsets.length; i++) {
            long encodedOffset = offsets[i];
            if (encodedOffset == 0) continue;
            sizeTotal += PositionCodec.decodeSize(encodedOffset);
        }

        if (sizeTotal == 0)
            return ret;

        MemorySegment segment = arena.allocate(sizeTotal, 512);

        List<MemorySegment> buffers = new ArrayList<>(offsets.length);
        List<Long> readOffsets = new ArrayList<>(offsets.length);

        int bufOffset = 0;
        for (int i = 0; i < offsets.length; i++) {
            long encodedOffset = offsets[i];
            if (encodedOffset == 0) continue;

            int length = PositionCodec.decodeSize(encodedOffset);
            long offset = PositionCodec.decodeOffset(encodedOffset);
            buffers.add(segment.asSlice(bufOffset, length));
            readOffsets.add(offset);
            bufOffset+=length;
        }

        uringFileReader.read(buffers, readOffsets);

        for (int i = 0, j=0; i < offsets.length; i++) {
            long encodedOffset = offsets[i];
            if (encodedOffset == 0) continue;
            ret[i] = new TermData(buffers.get(j++).asByteBuffer());
        }

        return ret;
    }

}

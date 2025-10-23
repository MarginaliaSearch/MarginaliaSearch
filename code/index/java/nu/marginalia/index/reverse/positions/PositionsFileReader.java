package nu.marginalia.index.reverse.positions;

import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.sequence.VarintCodedSequence;
import nu.marginalia.uring.UringFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;

/** Reads positions data from the positions file */
public class PositionsFileReader implements AutoCloseable {

    private final UringFileReader uringFileReader;
    private static final Logger logger = LoggerFactory.getLogger(PositionsFileReader.class);

    public PositionsFileReader(Path positionsFile) throws IOException {
        if (Boolean.getBoolean("index.directModePositionsFile")) {
            if ((Files.size(positionsFile) & 4095) != 0) {
                throw new IllegalArgumentException("Positions file is not block aligned in size: " + Files.size(positionsFile));
            }

            uringFileReader = new UringFileReader(positionsFile, true);
        }
        else {
            uringFileReader = new UringFileReader(positionsFile, false);
        }
    }

    @Override
    public void close() throws IOException {
        uringFileReader.close();
    }

    /** Get the positions for a keywords in the index, as pointed out by the encoded offsets;
     * intermediate buffers are allocated from the provided arena allocator. */
    public CodedSequence[] getTermData(Arena arena, IndexSearchBudget budget, long[] offsets) throws TimeoutException {

        int cnt = 0;

        for (int i = 0; i < offsets.length; i++) {
            long encodedOffset = offsets[i];
            if (encodedOffset == 0) continue;
            cnt++;
        }

        if (cnt == 0) {
            return new CodedSequence[offsets.length];
        }

        long[] readOffsets = new long[cnt];
        int[] readSizes = new int[cnt];

        for (int i = 0, j = 0; i < offsets.length; i++) {
            long encodedOffset = offsets[i];
            if (encodedOffset == 0) continue;

            readSizes[j] = PositionCodec.decodeSize(encodedOffset);
            readOffsets[j] = PositionCodec.decodeOffset(encodedOffset);
            j++;
        }

        List<MemorySegment> buffers = uringFileReader.readUnaligned(arena, budget.timeLeft(), readOffsets, readSizes, 4096);

        CodedSequence[] ret = new CodedSequence[offsets.length];

        for (int i = 0, j=0; i < offsets.length; i++) {
            long encodedOffset = offsets[i];
            if (encodedOffset == 0) continue;

            var buffer = buffers.get(j++).asByteBuffer();

            ret[i] = new VarintCodedSequence(buffer, 0, buffer.capacity());
        }

        return ret;
    }

}

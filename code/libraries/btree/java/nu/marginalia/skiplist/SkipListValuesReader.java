package nu.marginalia.skiplist;

import nu.marginalia.array.UringFileReader;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SkipListValuesReader implements AutoCloseable {
    private final UringFileReader uringFileReader;
    private final long fileSize;
    private static final int BLKSZ = 4096;
    public SkipListValuesReader(Path fileName) throws IOException {
        assert Files.exists(fileName);
        this.fileSize = Files.size(fileName);
        this.uringFileReader = new UringFileReader(fileName,  true);
    }

    public void close() {
        uringFileReader.close();
    }

    public void getValues(Arena arena, long[] values) {
        int blocksRequired = 0;
        long lastBlock = Long.MIN_VALUE;

        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) continue;

            long offset = values[i] - 1;
            long blockOffset = offset & -BLKSZ;
            int blockIdx = (int) (offset & (BLKSZ-1));
            if ((blockIdx & 7) != 0)
                throw new IllegalArgumentException(blockOffset + ":" + blockIdx);

            if (lastBlock != blockOffset) {
                blocksRequired++;
                lastBlock = blockOffset;
                if (!(fileSize >= (blockOffset + BLKSZ))) {
                    throw new IllegalStateException("Reading outsize of file size (not 512b padded?): " + (lastBlock + BLKSZ) + " >= " + fileSize);
                }
            }
        }

        if (blocksRequired == 0) {
            return;
        }

        MemorySegment buffer = arena.allocate(blocksRequired * BLKSZ, BLKSZ);

        List<MemorySegment> pages = new ArrayList<>(blocksRequired);
        List<Long> pageOffsets = new ArrayList<>(blocksRequired);

        lastBlock = Long.MIN_VALUE;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) continue;

            long offset = values[i] - 1;
            long blockOffset = offset & -BLKSZ;
            if (lastBlock != blockOffset) {
                var page = buffer.asSlice(pages.size() * BLKSZ, BLKSZ);
                pages.add(page);
                pageOffsets.add(blockOffset);
                lastBlock = blockOffset;
            }
        }

        uringFileReader.read(pages, pageOffsets);

        lastBlock = Long.MIN_VALUE;
        for (int i = 0, j = -1; i < values.length; i++) {
            if (values[i] == 0) continue;

            long offset = values[i] - 1;

            long blockOffset = offset & -BLKSZ;
            long blockIdx = offset & (BLKSZ-1);

            if (lastBlock != blockOffset) {
                j++;
                lastBlock = blockOffset;
            }

            values[i] = pages.get(j).get(ValueLayout.JAVA_LONG, blockIdx);
        }
    }
}

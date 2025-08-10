package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import nu.marginalia.uring.UringFileReader;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class IndexSpansReaderPlain implements IndexSpansReader {
    private final UringFileReader urinReader;

    public IndexSpansReaderPlain(Path spansFile) throws IOException {
        urinReader = new UringFileReader(spansFile,  false);
        urinReader.fadviseWillneed();
    }

    @Override
    public DocumentSpans readSpans(Arena arena, long encodedOffset) throws IOException {
        // for testing, slow
        return readSpans(arena, new long[] { encodedOffset})[0];
    }

    public DocumentSpans decode(MemorySegment ms) {
        int count = ms.get(ValueLayout.JAVA_INT, 0);
        int pos = 4;
        DocumentSpans ret = new DocumentSpans();

        // Decode each span
        for (int spanIdx = 0; spanIdx < count; spanIdx++) {
            byte code = ms.get(ValueLayout.JAVA_BYTE, pos);
            short len = ms.get(ValueLayout.JAVA_SHORT, pos+2);

            IntArrayList values = new IntArrayList(len);

            pos += 4;
            for (int i = 0; i < len; i++) {
                values.add(ms.get(ValueLayout.JAVA_INT, pos + 4*i));
            }
            ret.accept(code, values);
            pos += 4*len;
        }

        return ret;
    }

    @Override
    public DocumentSpans[] readSpans(Arena arena, long[] encodedOffsets) {

        long totalSize = 0;
        for (long offset : encodedOffsets) {
            if (offset < 0)
                continue;
            totalSize += SpansCodec.decodeSize(offset);
        }

        if (totalSize == 0) {
            return new DocumentSpans[encodedOffsets.length];
        }

        MemorySegment segment = arena.allocate(totalSize, 8);

        List<MemorySegment> buffers = new ArrayList<>(encodedOffsets.length);
        List<Long> offsets = new ArrayList<>(encodedOffsets.length);

        long bufferOffset = 0;

        for (long offset : encodedOffsets) {
            if (offset < 0)
                continue;

            long size = SpansCodec.decodeSize(offset);
            long start = SpansCodec.decodeStartOffset(offset);

            buffers.add(segment.asSlice(bufferOffset, size));
            offsets.add(start);
            bufferOffset += size;
        }

        DocumentSpans[] ret = new DocumentSpans[encodedOffsets.length];

        urinReader.read(buffers, offsets);

        for (int idx = 0, j = 0; idx < encodedOffsets.length; idx++) {
            if (encodedOffsets[idx] < 0)
                continue;
            ret[idx] = decode(buffers.get(j++));
        }

        return ret;
    }

    @Override
    public void close() throws IOException {
        urinReader.close();
    }

}

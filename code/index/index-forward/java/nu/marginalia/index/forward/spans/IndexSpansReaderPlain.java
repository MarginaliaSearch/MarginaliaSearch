package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.uring.UringFileReader;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class IndexSpansReaderPlain implements IndexSpansReader {
    private final UringFileReader uringReader;

    public IndexSpansReaderPlain(Path spansFile) throws IOException {
        if (Boolean.getBoolean("index.directModePositionsSpans")) {
            if ((Files.size(spansFile) & 4095) != 0) {
                throw new IllegalArgumentException("Spans file is not block aligned in size: " + Files.size(spansFile));
            }

            uringReader = new UringFileReader(spansFile,  true);
        }
        else {
            uringReader = new UringFileReader(spansFile,  false);
            uringReader.fadviseWillneed();
        }

    }

    @Override
    public DocumentSpans readSpans(Arena arena, long encodedOffset) throws IOException {
        // for testing, slow
        try {
            return readSpans(arena, new IndexSearchBudget(1000), new long[] { encodedOffset})[0];
        } catch (TimeoutException e) {
            throw new IOException(e);
        }
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
    public DocumentSpans[] readSpans(Arena arena, IndexSearchBudget budget, long[] encodedOffsets) throws TimeoutException {

        int readCnt = 0;
        for (long offset : encodedOffsets) {
            if (offset < 0)
                continue;
            readCnt ++;
        }

        if (readCnt == 0) {
            return new DocumentSpans[encodedOffsets.length];
        }

        long[] offsets = new long[readCnt];
        int[] sizes = new int[readCnt];

        for (int idx = 0, j = 0; idx < encodedOffsets.length; idx++) {
            if (encodedOffsets[idx] < 0)
                continue;
            long offset = encodedOffsets[idx];

            offsets[j] = SpansCodec.decodeStartOffset(offset);
            sizes[j] = SpansCodec.decodeSize(offset);
            j++;
        }

        List<MemorySegment> buffers = uringReader.readUnaligned(arena, budget.timeLeft(), offsets, sizes, 4096);

        DocumentSpans[] ret = new DocumentSpans[encodedOffsets.length];

        for (int idx = 0, j = 0; idx < encodedOffsets.length; idx++) {
            if (encodedOffsets[idx] < 0)
                continue;
            ret[idx] = decode(buffers.get(j++));
        }

        return ret;
    }

    @Override
    public void close() throws IOException {
        uringReader.close();
    }

}

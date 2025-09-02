package nu.marginalia.index.forward.spans;

import nu.marginalia.index.reverse.query.IndexSearchBudget;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

public interface IndexSpansReader extends AutoCloseable {
    @Deprecated
    DocumentSpans readSpans(Arena arena, long encodedOffset) throws IOException;

    DocumentSpans[] readSpans(Arena arena, IndexSearchBudget budget, long[] encodedOffsets) throws TimeoutException, IOException;

    static IndexSpansReader open(Path fileName) throws IOException {
        int version = SpansCodec.parseSpanFilesFooter(fileName);
        if (version == SpansCodec.SpansCodecVersion.COMPRESSED.ordinal()) {
            return new IndexSpansReaderCompressed(fileName);
        }
        else if (version == SpansCodec.SpansCodecVersion.PLAIN.ordinal()) {
            return new IndexSpansReaderPlain(fileName);
        }
        else {
            throw new IllegalArgumentException("Unsupported spans file version: " + version);
        }
    }

    void close() throws IOException;
}

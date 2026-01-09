package nu.marginalia.index.forward.spans;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Path;

public interface IndexSpansReader extends AutoCloseable {
    DecodableDocumentSpans readSpan(Arena arena, long encodedOffset);

    static IndexSpansReader open(Path fileName) throws IOException {
        int version = SpansCodec.parseSpanFilesFooter(fileName);
        if (version == SpansCodec.SpansCodecVersion.PLAIN.ordinal()) {
            return new IndexSpansReaderPlain(fileName);
        }
        else {
            throw new IllegalArgumentException("Unsupported spans file version: " + version);
        }
    }

    void close() throws IOException;
}

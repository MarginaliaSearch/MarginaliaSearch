package nu.marginalia.index.forward.spans;

import java.io.IOException;
import java.lang.foreign.Arena;

public interface ForwardIndexSpansReader extends AutoCloseable {
    DocumentSpans readSpans(Arena arena, long encodedOffset) throws IOException;
    DocumentSpans[] readSpans(Arena arena, long[] encodedOffsets) throws IOException;
}

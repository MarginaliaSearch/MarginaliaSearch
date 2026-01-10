package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class DecodableDocumentSpans {
    @Nullable
    private final MemorySegment segment;

    public DecodableDocumentSpans(@Nonnull MemorySegment segment) {
        this.segment = segment;
    }

    public DecodableDocumentSpans() {
        this.segment = null;
    }

    public DocumentSpans decode(Int2ObjectFunction<IntArrayList> allocator) {
        if (segment == null)
            return new DocumentSpans();

        int count = segment.get(ValueLayout.JAVA_INT, 0);
        int pos = 4;
        DocumentSpans ret = new DocumentSpans();

        // Decode each span
        for (int spanIdx = 0; spanIdx < count; spanIdx++) {
            byte code = segment.get(ValueLayout.JAVA_BYTE, pos);
            short len = segment.get(ValueLayout.JAVA_SHORT, pos+2);

            IntArrayList values = allocator.get(len);

            pos += 4;
            for (int i = 0; i < len; i++) {
                values.add(segment.get(ValueLayout.JAVA_INT, pos + 4*i));
            }
            ret.accept(code, values);
            pos += 4*len;
        }

        return ret;
    }

}

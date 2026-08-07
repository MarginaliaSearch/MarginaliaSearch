package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import nu.marginalia.index.ScratchIntList;

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

    public DocumentSpans decode(Int2ObjectFunction<ScratchIntList> allocator) {
        if (segment == null)
            return new DocumentSpans();

        // Unaligned layouts throughout, as the segment may be a slice of a mapped
        // file at an arbitrary byte offset
        int count = segment.get(ValueLayout.JAVA_INT_UNALIGNED, 0);
        int pos = 4;
        DocumentSpans ret = new DocumentSpans();

        // Decode each span
        for (int spanIdx = 0; spanIdx < count; spanIdx++) {
            byte code = segment.get(ValueLayout.JAVA_BYTE, pos);
            short len = segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, pos+2);

            ScratchIntList values = allocator.get(len);

            pos += 4;

            // Bulk copying replaces a liveness and bounds checked read per element,
            // and the raw size setter avoids a zero fill the copy would overwrite
            values.setSizeForOverwrite(len);
            MemorySegment.copy(segment, ValueLayout.JAVA_INT_UNALIGNED, pos, values.elements(), 0, len);

            ret.accept(code, values);
            pos += 4*len;
        }

        return ret;
    }

}

package nu.marginalia.model.idx;

import java.util.EnumSet;

public enum DocumentFlags {
    /** Simple processing was done, this document should be de-prioritized as a search result */
    Simple,

    PlainText,
    UnusedBit2,
    UnusedBit3,
    UnusedBit4,
    UnusedBit5,
    UnusedBit6,
    UnusedBit7,
    ;

    public int asBit() {
        return 1 << ordinal();
    }

    public boolean isPresent(long value) {
        return (asBit() & value) > 0;
    }

    public static EnumSet<DocumentFlags> decode(long encodedValue) {
        EnumSet<DocumentFlags> ret = EnumSet.noneOf(DocumentFlags.class);

        for (DocumentFlags f : values()) {
            if ((encodedValue & f.asBit()) > 0) {
                ret.add(f);
            }
        }

        return ret;
    }
}

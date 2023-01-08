package nu.marginalia.wmsa.edge.index.model;

import java.util.EnumSet;

public enum EdgePageDocumentFlags {
    /** Simple processing was done, this document should be de-prioritized as a search result */
    Simple,

    UnusedBit1,
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

    public static EnumSet<EdgePageDocumentFlags> decode(long encodedValue) {
        EnumSet<EdgePageDocumentFlags> ret = EnumSet.noneOf(EdgePageDocumentFlags.class);

        for (EdgePageDocumentFlags f : values()) {
            if ((encodedValue & f.asBit()) > 0) {
                ret.add(f);
            }
        }

        return ret;
    }
}

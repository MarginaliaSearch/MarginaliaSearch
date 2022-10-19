package nu.marginalia.wmsa.edge.index.model;

import java.util.EnumSet;

public enum EdgePageWordFlags {
    Title,
    Subjects,
    NamesWords,
    Site,
    SiteAdjacent,
    Simple;

    public int asBit() {
        return 1 << ordinal();
    }

    public boolean isPresent(long value) {
        return (asBit() & value) > 0;
    }

    public static EnumSet<EdgePageWordFlags> decode(long encodedValue) {
        EnumSet<EdgePageWordFlags> ret = EnumSet.noneOf(EdgePageWordFlags.class);

        for (EdgePageWordFlags f : values()) {
            if ((encodedValue & f.asBit()) > 0) {
                ret.add(f);
            }
        }

        return ret;
    }
}

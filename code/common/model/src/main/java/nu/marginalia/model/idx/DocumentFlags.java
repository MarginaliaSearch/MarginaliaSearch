package nu.marginalia.model.idx;

import java.util.EnumSet;

public enum DocumentFlags {
    Javascript,
    PlainText,
    GeneratorDocs,
    GeneratorForum,
    GeneratorWiki,
    Unused6,
    Unused7,
    Unused8,
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
            if ((encodedValue & f.asBit() & 0xff) > 0) {
                ret.add(f);
            }
        }

        return ret;
    }
}

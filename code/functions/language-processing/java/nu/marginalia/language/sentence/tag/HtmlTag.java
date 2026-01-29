package nu.marginalia.language.sentence.tag;

import java.util.Arrays;

public enum HtmlTag {
    ANCHOR((byte) 'a', false, false),
    TITLE((byte) 't', false, false),
    HEADING((byte) 'h', false, false),
    CODE((byte) 'c', false, true),
    NAV((byte) 'n', false, false),

    // pseudo-tags for internal use,
    BODY((byte) 'b', false, false),
    EXTERNAL_LINKTEXT((byte) 'x', false, false),
    DOC_URL((byte) 'u', false, false),

    // excluded tags must be put last!
    FORM((byte) 0, true, false),
    SCRIPT((byte) 0, true, false),
    STYLE((byte) 0, true, false),
    ;

    public final byte code;
    public final boolean exclude;
    public final boolean nonLanguage;

    HtmlTag(byte code, boolean exclude, boolean nonLanguage) {
        this.code = code;
        this.exclude = exclude;
        this.nonLanguage = nonLanguage;
    }

    // This is a bit of a hack to get the included tags in the order they are defined in the enum
    public static final HtmlTag[] includedTags;

    static {
        HtmlTag[] values = values();
        includedTags = new HtmlTag[(int) Arrays.stream(values).filter(tag -> !tag.exclude).count()];

        for (int i = 0; i < values.length; i++) {
            if (i != values[i].ordinal()) {
                throw new IllegalStateException("Excluded tags must be put last");
            }

            if (!values()[i].exclude) {
                includedTags[i] = values()[i];
            }
        }
    }
}

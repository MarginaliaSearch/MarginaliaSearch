package nu.marginalia.language.sentence.tag;

public enum HtmlTag {
    FORM((byte) 0, true, false),
    SCRIPT((byte) 0, true, false),
    STYLE((byte) 0, true, false),

    ANCHOR((byte) 'a', false, false),
    TITLE((byte) 't', false, false),
    HEADING((byte) 'h', false, false),
    CODE((byte) 'c', false, true),
    NAV((byte) 'n', false, false),

    // pseudo-tags for internal use
    EXTERNAL_LINKTEXT((byte) 'x', false, false),

    ;

    public byte code;
    public boolean exclude;
    public boolean nonLanguage;

    HtmlTag(byte code, boolean exclude, boolean nonLanguage) {
        this.code = code;
        this.exclude = exclude;
        this.nonLanguage = nonLanguage;
    }

}

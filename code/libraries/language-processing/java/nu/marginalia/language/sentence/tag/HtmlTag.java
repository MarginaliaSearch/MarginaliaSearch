package nu.marginalia.language.sentence.tag;

public enum HtmlTag {
    SCRIPT((byte) 's', true, false),
    STYLE((byte) 'S', true, false),
    CODE((byte) 'c', false, true),
    PRE((byte) 'p', false, true),
    TITLE((byte) 't', false, false),
    HEADING((byte) 'h', false, false),
    NAV((byte) 'n', false, false),
    PAGE_HEADER((byte) 'H',false, false),
    PAGE_FOOTER((byte) 'f', false, false);

    public byte code;
    public boolean exclude;
    public boolean nonLanguage;

    HtmlTag(byte code, boolean exclude, boolean nonLanguage) {
        this.code = code;
        this.exclude = exclude;
        this.nonLanguage = nonLanguage;
    }

}

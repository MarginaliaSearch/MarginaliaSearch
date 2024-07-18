package nu.marginalia.language.sentence.tag;

public enum HtmlTag {
    SCRIPT(true, false),
    STYLE(true, false),
    CODE(false, true),
    PRE(false, true),
    TITLE(false, false),
    HEADING(false, false),
    NAV(false, false),
    HEADER(false, false),
    FOOTER(false, false);

    public boolean exclude;
    public boolean nonLanguage;

    HtmlTag(boolean exclude, boolean nonLanguage) {
        this.exclude = exclude;
        this.nonLanguage = nonLanguage;
    }
}

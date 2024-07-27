package nu.marginalia.language.sentence.tag;

public enum HtmlTag {
    SCRIPT('s', true, false),
    STYLE('S', true, false),
    CODE('c', false, true),
    PRE('p', false, true),
    TITLE('t', false, false),
    HEADING('h', false, false),
    NAV('n', false, false),
    HEADER('H',false, false),
    FOOTER('f', false, false);

    public char code;
    public boolean exclude;
    public boolean nonLanguage;

    HtmlTag(char code, boolean exclude, boolean nonLanguage) {
        this.code = code;
        this.exclude = exclude;
        this.nonLanguage = nonLanguage;
    }

}

package nu.marginalia.language.sentence.tag;

import java.util.EnumSet;

public class HtmlTaggedString {
    private StringBuilder string;
    private final EnumSet<HtmlTag> tags;

    public HtmlTaggedString(StringBuilder string, EnumSet<HtmlTag> tags) {
        this.tags = tags;
        this.string = string;
    }

    public String string() {
        return string.toString();
    }

    public EnumSet<HtmlTag> tags() {
        return tags;
    }

    public void append(String s) {
        string.append(' ').append(s);
    }

    public String toString() {
        return "[" + tags.toString() + ":" + string.toString() + "]";
    }

    public int length() {
        return string.length();
    }
}

package nu.marginalia.gemini.gmi.parser;

import nu.marginalia.gemini.gmi.line.GemtextAside;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;

import java.util.regex.Pattern;

public class GemtextAsideParser {
    private static final Pattern listItemPattern = Pattern.compile("^\\((.*)\\)$");

    public static GemtextAside parse(String s, MemexNodeHeadingId heading) {
        var matcher = listItemPattern.matcher(s);

        if (!matcher.matches()) {
            return null;
        }

        return new GemtextAside(matcher.group(1), heading);
    }
}

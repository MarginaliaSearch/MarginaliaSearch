package nu.marginalia.memex.gemini.gmi.parser;

import nu.marginalia.memex.gemini.gmi.line.GemtextAside;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;

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

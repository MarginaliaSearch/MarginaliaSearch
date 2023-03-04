package nu.marginalia.memex.gemini.gmi.parser;

import nu.marginalia.memex.gemini.gmi.line.AbstractGemtextLine;
import nu.marginalia.memex.gemini.gmi.line.GemtextHeading;
import nu.marginalia.memex.gemini.gmi.line.GemtextText;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;

import java.util.regex.Pattern;

public class GemtextHeadingParser {
    private static final Pattern headingPattern = Pattern.compile("^(#+)\\s*([^#].*|$)$");

    public static AbstractGemtextLine parse(String s, MemexNodeHeadingId heading) {
        var matcher = headingPattern.matcher(s);

        if (!matcher.matches()) {
            return new GemtextText(s, heading);
        }

        int level = matcher.group(1).length() - 1;
        var newHeading = heading.next(level);

        return new GemtextHeading(newHeading, matcher.group(2), newHeading);
    }

}

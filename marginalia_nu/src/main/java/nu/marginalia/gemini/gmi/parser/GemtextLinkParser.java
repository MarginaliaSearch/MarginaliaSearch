package nu.marginalia.gemini.gmi.parser;

import nu.marginalia.gemini.gmi.line.AbstractGemtextLine;
import nu.marginalia.gemini.gmi.line.GemtextLink;
import nu.marginalia.gemini.gmi.line.GemtextText;
import nu.marginalia.wmsa.memex.model.MemexExternalUrl;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;
import nu.marginalia.wmsa.memex.model.MemexUrl;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

public class GemtextLinkParser {
    private static Pattern linkPattern = Pattern.compile("^=>\\s?([^\\s]+)\\s*(.+)?$");

    @Nullable
    public static AbstractGemtextLine parse(String s, MemexNodeHeadingId heading) {
        var matcher = linkPattern.matcher(s);

        if (!matcher.matches()) {
            return new GemtextText(s, heading);
        }
        if (matcher.groupCount() == 2) {
            return new GemtextLink(toMemexUrl(matcher.group(1)), matcher.group(2), heading);
        }
        else {
            return new GemtextLink(toMemexUrl(matcher.group(1)), null, heading);
        }
    }

    private static MemexUrl toMemexUrl(String url) {
        if (url.startsWith("/")) {
            return new MemexNodeUrl(url);
        }
        else {
            return new MemexExternalUrl(url);
        }
    }


}

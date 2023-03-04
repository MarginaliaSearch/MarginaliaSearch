package nu.marginalia.memex.gemini.gmi.parser;

import nu.marginalia.memex.gemini.gmi.line.AbstractGemtextLine;
import nu.marginalia.memex.gemini.gmi.line.GemtextLink;
import nu.marginalia.memex.gemini.gmi.line.GemtextText;
import nu.marginalia.memex.memex.model.MemexExternalUrl;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import nu.marginalia.memex.memex.model.MemexUrl;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

public class GemtextLinkParser {
    private static final Pattern linkPattern = Pattern.compile("^=>\\s?([^\\s]+)\\s*(.+)?$");

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

package nu.marginalia.gemini.gmi.parser;

import java.util.regex.Pattern;

public class GemtextListParser {
    private static final Pattern listItemPattern = Pattern.compile("^\\*\\s?(.+)$");

    public static String parse(String s) {
        var matcher = listItemPattern.matcher(s);

        if (!matcher.matches()) {
            return null;
        }

        return matcher.group(1);
    }
}

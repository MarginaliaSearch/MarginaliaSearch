package nu.marginalia.converting.processor.logic;

import nu.marginalia.model.EdgeUrl;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PlainTextLogic {

    public String getDescription(List<String> firstFewLines) {
        return StringUtils.truncate(firstFewLines.stream().filter(this::looksLikeText)
                .collect(Collectors.joining(" ")).replaceAll("\\s+", " ")

                , 255);
    }

    private boolean looksLikeText(String s) {
        s = s.trim();
        if (s.length() < 16)
            return false;
        return 4 * s.chars().filter(Character::isAlphabetic).count() > 3L * s.length();

    }

    public String getTitle(EdgeUrl url, List<String> firstFewLines) {
        List<String> candidates = new ArrayList<>(firstFewLines);

        // Remove mailing list header type stuff
        candidates.removeIf(line -> line.contains(":"));

        for (int line = 1; line < candidates.size(); line++) {
            String maybeUnderline = candidates.get(line);
            if (isUnderline(maybeUnderline)) {
                String titleCandidate = candidates.get(line - 1).trim();
                if (titleCandidate.length() > 16) {
                    return StringUtils.truncate(titleCandidate, 128);
                }
            }
        }

        for (var line : firstFewLines) {
            if (isSideline(line)) {
                return line.replaceAll("[^a-zA-Z0-9]+", " ").trim();
            }
        }

        return url.path.substring(url.path.lastIndexOf('/'));
    }

    public boolean isSideline(String s) {

        // detector for
        // ==== HEADER ====
        // -style headings

        int start, end;
        for (start = 0; start < s.length(); start++) {
            if (!Character.isWhitespace(s.charAt(start))) break;
        }
        for (end = s.length() - 1; end > start; end--) {
            if (!Character.isWhitespace(s.charAt(start))) break;
        }

        if (end - start < 8)
            return false;

        int c = s.charAt(start);
        if ("=_*".indexOf(c) < 0) {
            return false;
        }
        if (c != s.charAt(end)) {
            return false;
        }

        for (; start < end && s.charAt(start) == c; start++);

        if (end - start < 4)
            return false;

        for (; end > start && s.charAt(end) == c; --end);

        if (end - start < 4)
            return false;

        return true;
    }
    public boolean isUnderline(String s) {
        int start, end;
        for (start = 0; start < s.length(); start++) {
            if (!Character.isWhitespace(s.charAt(start))) break;
        }
        for (end = s.length() - 1; end > start; end--) {
            if (!Character.isWhitespace(s.charAt(start))) break;
        }
        if (end - start < 8)
            return false;

        if ("=_*".indexOf(s.charAt(start)) < 0) {
            return false;
        }

        int c = s.charAt(start);

        for (int i = start; i < end; i++) {
            if (c != s.charAt(i))
                return false;
        }

        return true;
    }

}

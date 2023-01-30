package nu.marginalia.util.language.processing.sentence;

import java.util.Arrays;
import java.util.Objects;

public class SentenceExtractorStringUtils {

    public static String sanitizeString(String s) {
        char[] newChars = new char[s.length()];
        int pi = 0;
        boolean changed = false;
        for (int i = 0; i < newChars.length; i++) {
            char c = s.charAt(i);
            if (!isBadChar(c)) {
                newChars[pi++] = c;
            }
            else {
                changed = true;
                newChars[pi++] = ' ';
            }
        }

        if (changed) {
            s = new String(newChars, 0, pi);
        }

        if (s.startsWith(".")) {
            s = s.substring(1);
        }

        if (s.isBlank()) {
            return "";
        }

        return s;

    }

    private static boolean isBadChar(char c) {
        if (c >= 'a' && c <= 'z') return false;
        if (c >= 'A' && c <= 'Z') return false;
        if (c >= '0' && c <= '9') return false;
        if ("_#@.".indexOf(c) >= 0) return false;
        if (c >= '\u00C0' && c <= '\u00D6') return false;
        if (c >= '\u00D8' && c <= '\u00F6') return false;
        if (c >= '\u00F8' && c <= '\u00FF') return false;

        return true;
    }

    public static String normalizeSpaces(String s) {
        if (s.indexOf('\t') >= 0) {
            s = s.replace('\t', ' ');
        }
        if (s.indexOf('\n') >= 0) {
            s = s.replace('\n', ' ');
        }
        return s;
    }


    public static String toLowerCaseStripPossessive(String word) {
        String val = stripPossessive(word).toLowerCase();

        if (Objects.equals(val, word)) {
            return word;
        }

        return val;
    }

    public static String[] toLowerCaseStripPossessive(String[] words) {
        String[] lc = new String[words.length];
        Arrays.setAll(lc, i ->SentenceExtractorStringUtils.toLowerCaseStripPossessive(words[i]));
        return lc;
    }

    public static String stripPossessive(String s) {
        int end = s.length();

        if (s.endsWith("'")) {
            return s.substring(0, end-1);
        }

        if (s.endsWith("'s") || s.endsWith("'S")) {
            return s.substring(0, end-2);
        }

        return s;
    }


}

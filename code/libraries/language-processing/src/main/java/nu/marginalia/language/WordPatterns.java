package nu.marginalia.language;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Regular expression patterns for deciding which words are eligible to be keywords.
 * <p/>
 * This is in dire need of oversight. Here be towering dragons with names,
 * a skull next to their HP bar, and their own Mick Gordon soundtrack just
 * for the battle.
 *
 */
public class WordPatterns {
    public static final int MIN_WORD_LENGTH = 1;
    public static final int MAX_WORD_LENGTH = 64;

    public static final String WORD_TOKEN_JOINER = "_";


    public static final Set<String> topWords;
    static {
        topWords = new HashSet<>(200, 0.25f);
        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("dictionary/en-stopwords"),
                "Could not load word frequency table");
             var br = new BufferedReader(new InputStreamReader(resource))
        ) {
            while (true) {
                String s = br.readLine();
                if (s == null) {
                    break;
                }
                topWords.add(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean hasMoreThanTwo(String s, char c, int max) {
        int idx = 0;
        for (int i = 0; i <= max; i++) {
            idx = s.indexOf(c, idx+1);
            if (idx < 0 || idx >= s.length() - 1)
                return false;
        }
        return true;
    }


    public static boolean filter(String word) {
        if (word.isBlank()) {
            return false;
        }
        if (hasMoreThanTwo(word, '-', 4)) {
            return false;
        }
        if (hasMoreThanTwo(word, '+', 2)) {
            return false;
        }
        if (word.startsWith("-")
                || word.endsWith("-")
        ) {
            return false;
        }

        int numDigits = 0;
        for (int i = 0; i < word.length(); i++) {
            if (Character.isDigit(word.charAt(i))) {
                numDigits++;
            }
            if (numDigits > 16)
                return false;
        }

        return true;
    }

    public static boolean hasWordQualities(String s) {
        if (s.isBlank())
            return false;

        int start = 0;
        int end = s.length();
        if (s.charAt(0) == '#') start++;
        if (end > 1 && s.charAt(end-1) == '#') end--;

        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            if (("_@.'+-".indexOf(c) < 0)
                && !(c >= 'a' && c <= 'z')
                && !(c >= 'A' && c <= 'Z')
                && !(c >= '0' && c <= '9')
                && !(c >= '\u00C0' && c <= '\u00D6')
                && !(c >= '\u00D8' && c <= '\u00f6')
                && !(c >= '\u00f8' && c <= '\u00ff'))
            {
                        return false;
            }
        }

        return true;
    }

    public static boolean isStopWord(String s) {
        if (s.length() < MIN_WORD_LENGTH) {
            return true;
        }
        if (!hasWordQualities(s)) {
            return true;
        }
        if (!filter(s)) {
            return true;
        }

        String sLc;
        if (StringUtils.isAllLowerCase(s)) {
            sLc = s;
        }
        else {
            sLc = s.toLowerCase();
        }

        if (isTopWord(sLc)) {
            return true;
        }

        return false;
    }

    public static boolean isTopWord(String strLowerCase) {
        return topWords.contains(strLowerCase);
    }

}

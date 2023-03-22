package nu.marginalia.language;

import org.apache.commons.lang3.StringUtils;

/** Logic for deciding which words are eligible to be keywords.
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
    private static final WordDictionary stopWords =
            WordDictionary.fromClasspathResource("dictionary/en-stopwords");

    /** Run checks on the word and exclude terms with too many special characters
     */
    public static boolean isNotJunkWord(String word) {
        if (word.isBlank()) {
            return false;
        }
        if (hasMoreThanN(word, '-', 4)) {
            return false;
        }
        if (hasMoreThanN(word, '+', 2)) {
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

    private static boolean hasMoreThanN(String s, char c, int max) {
        int idx = 0;
        for (int i = 0; i <= max; i++) {
            idx = s.indexOf(c, idx+1);
            if (idx < 0 || idx >= s.length() - 1)
                return false;
        }
        return true;
    }

    public static boolean isStopWord(String s) {
        if (s.length() < MIN_WORD_LENGTH) {
            return true;
        }

        if (!isNotJunkWord(s)) {
            return true;
        }

        String sLc;
        if (StringUtils.isAllLowerCase(s)) {
            sLc = s;
        }
        else {
            sLc = s.toLowerCase();
        }

        if (stopWords.contains(sLc)) {
            return true;
        }

        return false;
    }


}

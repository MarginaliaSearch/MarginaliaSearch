package nu.marginalia.language;

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

    // Stopword exclusion has been moved to the index.  We just filter out
    // junk words here now.
    public static boolean isStopWord(String s) {
        if (!isNotJunkWord(s)) {
            return true;
        }

        return false;
    }


}

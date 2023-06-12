package nu.marginalia.summary.heuristic;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

public class HeuristicTextUtil {

    /** Return the number of occurrences of any word in the set of words in the text.
     *
     * The words must be all lower case, the text may be in any case. To count as a match,
     * the word must be surrounded by non-alphabetic characters.
     *
     */
    public static int countOccurrencesOfAnyWord(String text, Collection<String> wordsLc) {
        if (StringUtils.isAllLowerCase(text)) {
            return countOccurrencesOfAnyWordLowerCase(text, wordsLc);
        }

        int cnt = 0;
        for (var word : wordsLc) {
            if (containsWordInAnyCase(text, word)) {
                cnt++;
            }
        }
        return cnt;
    }

    public static boolean containsWordInAnyCase(String text, String wordLowerCase) {
        int pos = StringUtils.indexOfIgnoreCase(text, wordLowerCase);
        int wl = wordLowerCase.length();

        while (pos >= 0) {
            if (pos > 0) {
                char c = text.charAt(pos - 1);
                if (Character.isAlphabetic(c) || Character.isDigit(c)) {
                    pos = StringUtils.indexOfIgnoreCase(text, wordLowerCase, pos + 1);
                    continue;
                }
            }
            if (pos + wl < text.length()) {
                char c = text.charAt(pos + wl);
                if (Character.isAlphabetic(c) || Character.isDigit(c)) {
                    pos = StringUtils.indexOfIgnoreCase(text, wordLowerCase, pos + 1);
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    public static int countOccurrencesOfAnyWordLowerCase(String textLc, Collection<String> wordsLc) {
        int cnt = 0;
        for (var word : wordsLc) {
            if (containsWordAllLowerCase(textLc, word)) {
                cnt++;
            }
        }
        return cnt;
    }

    public static boolean containsWordAllLowerCase(String text, String wordLowerCase) {
        int pos = text.indexOf(wordLowerCase);
        int wl = wordLowerCase.length();

        while (pos >= 0) {
            if (pos > 0) {
                char c = text.charAt(pos - 1);
                if (Character.isAlphabetic(c) || Character.isDigit(c)) {
                    pos = text.indexOf(wordLowerCase, pos + 1);
                    continue;
                }
            }
            if (pos + wl < text.length()) {
                char c = text.charAt(pos + wl);
                if (Character.isAlphabetic(c) || Character.isDigit(c)) {
                    pos = text.indexOf(wordLowerCase, pos + 1);
                    continue;
                }
            }
            return true;
        }
        return false;
    }

}

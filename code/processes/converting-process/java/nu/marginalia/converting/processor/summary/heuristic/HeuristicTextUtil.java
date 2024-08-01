package nu.marginalia.converting.processor.summary.heuristic;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

public class HeuristicTextUtil {

    /** Return the number of occurrences of any word in the set of words in the text.
     * <p>
     * The words must be all lower case, the text may be in any case. To count as a match,
     * the word must be surrounded by non-alphabetic characters.
     * <p>
     * Since this is a very  hot method, it makes best-effort assumptions that may return false positives,
     * negatives; or both.
     */
    public static int countOccurrencesOfAnyWord(String text, Collection<String> wordsLc) {
        if (StringUtils.isAllLowerCase(text)) {
            return countOccurrencesOfAnyWordLowerCase(text, wordsLc);
        }

        int cnt = 0;

        if (StringUtils.isAsciiPrintable(text)) {
            for (var word : wordsLc) {
                if (containsWordInAnyCaseASCII(text, word)) {
                    cnt++;
                }
            }
        }
        else {
            for (var word : wordsLc) {
                if (containsWordInAnyCase(text, word)) {
                    cnt++;
                }
            }
        }

        return cnt;
    }

    public static boolean containsWordInAnyCase(String text, String wordLowerCase) {
        int pos = indexOfIgnoreCaseAny(text, wordLowerCase, 0);
        int wl = wordLowerCase.length();

        while (pos >= 0) {
            if (pos > 0) {
                char c = text.charAt(pos - 1);
                if (Character.isAlphabetic(c) || Character.isDigit(c)) {
                    pos = indexOfIgnoreCaseAny(text, wordLowerCase, pos + 1);
                    continue;
                }
            }
            if (pos + wl < text.length()) {
                char c = text.charAt(pos + wl);
                if (Character.isAlphabetic(c) || Character.isDigit(c)) {
                    pos = indexOfIgnoreCaseAny(text, wordLowerCase, pos + 1);
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean containsWordInAnyCaseASCII(String text, String wordLowerCase) {
        int pos = indexOfIgnoreCaseASCII(text, wordLowerCase, 0);
        int wl = wordLowerCase.length();

        while (pos >= 0) {
            if (pos > 0) {
                char c = text.charAt(pos - 1);
                if (Character.isAlphabetic(c) || Character.isDigit(c)) {
                    pos = indexOfIgnoreCaseASCII(text, wordLowerCase, pos + 1);
                    continue;
                }
            }
            if (pos + wl < text.length()) {
                char c = text.charAt(pos + wl);
                if (Character.isAlphabetic(c) || Character.isDigit(c)) {
                    pos = indexOfIgnoreCaseASCII(text, wordLowerCase, pos + 1);
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    private static int indexOfIgnoreCaseASCII(String text, String wordLowerCase, int i) {
        if (wordLowerCase.length() == 0)
            return 0;

        char first = wordLowerCase.charAt(0);

        outer:
        for (int idx = i, end = text.length() - wordLowerCase.length() + 1; idx < end; idx++) {
            if (toLowerASCII(text.charAt(idx)) != first)
                continue;

            for (int j = 1; j < wordLowerCase.length(); j++) {
                if (toLowerASCII(text.charAt(idx + j)) != wordLowerCase.charAt(j))
                    continue outer;
            }

            return idx;
        }

        return -1;
    }

    public static char toLowerASCII(char c) {
        if (c > 64 && c < 91) {
            return (char) (c | 32);
        }
        return c;
    }

    private static int indexOfIgnoreCaseAny(String text, String wordLowerCase, int i) {
        if (wordLowerCase.length() == 0)
            return 0;

        char first = wordLowerCase.charAt(0);

        outer:
        for (int idx = i, end = text.length() - wordLowerCase.length() + 1; idx < end; idx++) {
            if (Character.toLowerCase(text.charAt(idx)) != first)
                continue;

            for (int j = 1; j < wordLowerCase.length(); j++) {
                if (Character.toLowerCase(text.charAt(idx + j)) != wordLowerCase.charAt(j))
                    continue outer;
            }

            return idx;
        }

        return -1;
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

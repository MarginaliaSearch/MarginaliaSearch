/*
 * Copyright (c) 2016 Carmen Alvarez + modifications from Viktor Lofgren
 *
 * This file is part of Porter Stemmer.
 *
 * Porter Stemmer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Porter Stemmer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Porter Stemmer.  If not, see <http://www.gnu.org/licenses/>.
 */

package ca.rmen.porterstemmer;

import java.util.Locale;

/**
 * This is a simple implementation of the Porter stemming algorithm, defined here:
 * <a href="http://tartarus.org/martin/PorterStemmer/def.txt">http://tartarus.org/martin/PorterStemmer/def.txt</a>
 * <p>
 * This is an optimized version that uses a StringBuilder to go fast.
 */
public class PorterStemmer {

    private static final String[] STEP2_SUFFIXES = {
            "ational",
            "tional",
            "enci",
            "anci",
            "izer",
            "bli", // the published algorithm specifies abli instead of bli.
            "alli",
            "entli",
            "eli",
            "ousli",
            "ization",
            "ation",
            "ator",
            "alism",
            "iveness",
            "fulness",
            "ousness",
            "aliti",
            "iviti",
            "biliti",
            "logi", // the published algorithm doesn't contain this
    };
    private static final String[] STEP2_REPLACEMENTS = {
            "ate",
            "tion",
            "ence",
            "ance",
            "ize",
            "ble", // the published algorithm specifies able instead of ble
            "al",
            "ent",
            "e",
            "ous",
            "ize",
            "ate",
            "ate",
            "al",
            "ive",
            "ful",
            "ous",
            "al",
            "ive",
            "ble",
            "log" // the published algorithm doesn't contain this
    };

    private static final String[] STEP3_SUFFIXES = {
            "icate",
            "ative",
            "alize",
            "iciti",
            "ical",
            "ful",
            "ness",
    };
    private static final String[] STEP3_REPLACEMENTS = {
            "ic",
            "",
            "al",
            "ic",
            "ic",
            "",
            "",
    };

    private static final String[] STEP4_SUFFIXES = {
            "al",
            "ance",
            "ence",
            "er",
            "ic",
            "able",
            "ible",
            "ant",
            "ement",
            "ment",
            "ent",
            "ion",
            "ou",
            "ism",
            "ate",
            "iti",
            "ous",
            "ive",
            "ize",
    };

    /**
     * @param word the word to stem
     * @return the stem of the word, in lowercase.
     */
    public String stemWord(String word) {
        String stem = word.toLowerCase(Locale.getDefault());
        if (stem.length() < 3) return stem;
        StringBuilder sb = new StringBuilder(stem);
        stemStep1a(sb);
        stemStep1b(sb);
        stemStep1c(sb);
        stemStep2(sb);
        stemStep3(sb);
        stemStep4(sb);
        stemStep5a(sb);
        stemStep5b(sb);
        return sb.toString();
    }

    void stemStep1a(StringBuilder sb) {
        // SSES -> SS
        if (endsWith(sb, "sses")) {
            sb.setLength(sb.length() - 2);
            return;
        }
        // IES  -> I
        if (endsWith(sb, "ies")) {
            sb.setLength(sb.length() - 2);
            return;
        }
        // SS   -> SS
        if (endsWith(sb, "ss")) {
            return;
        }
        // S    ->
        if (endsWith(sb, "s")) {
            sb.setLength(sb.length() - 1);
        }
    }

    void stemStep1b(StringBuilder sb) {
        // (m>0) EED -> EE
        if (endsWith(sb, "eed")) {
            int stemLen = sb.length() - 1;
            int m = getM(sb, stemLen);
            if (m > 0) sb.setLength(stemLen);
            return;
        }
        // (*v*) ED  ->
        if (endsWith(sb, "ed")) {
            int stemLen = sb.length() - 2;
            if (containsVowel(sb, stemLen)) {
                sb.setLength(stemLen);
                step1b2(sb);
            }
            return;
        }
        // (*v*) ING ->
        if (endsWith(sb, "ing")) {
            int stemLen = sb.length() - 3;
            if (containsVowel(sb, stemLen)) {
                sb.setLength(stemLen);
                step1b2(sb);
            }
        }
    }

    private void step1b2(StringBuilder sb) {
        // AT -> ATE
        if (endsWith(sb, "at")) {
            sb.append('e');
        }
        // BL -> BLE
        else if (endsWith(sb, "bl")) {
            sb.append('e');
        }
        // IZ -> IZE
        else if (endsWith(sb, "iz")) {
            sb.append('e');
        } else {
            // (*d and not (*L or *S or *Z))
            // -> single letter
            char lastDoubleConsonant = getLastDoubleConsonant(sb);
            if (lastDoubleConsonant != 0 &&
                    lastDoubleConsonant != 'l'
                    && lastDoubleConsonant != 's'
                    && lastDoubleConsonant != 'z') {
                sb.setLength(sb.length() - 1);
            }
            // (m=1 and *o) -> E
            else {
                int m = getM(sb, sb.length());
                if (m == 1 && isStarO(sb, sb.length())) {
                    sb.append('e');
                }

            }
        }
    }

    void stemStep1c(StringBuilder sb) {
        if (endsWith(sb, "y")) {
            int stemLen = sb.length() - 1;
            if (containsVowel(sb, stemLen)) sb.setCharAt(stemLen, 'i');
        }
    }

    void stemStep2(StringBuilder sb) {
        // (m>0) ATIONAL ->  ATE
        // (m>0) TIONAL  ->  TION
        for (int i = 0; i < STEP2_SUFFIXES.length; i++) {
            if (endsWith(sb, STEP2_SUFFIXES[i])) {
                int stemLen = sb.length() - STEP2_SUFFIXES[i].length();
                int m = getM(sb, stemLen);
                if (m > 0) {
                    sb.setLength(stemLen);
                    sb.append(STEP2_REPLACEMENTS[i]);
                }
                return;
            }
        }
    }

    void stemStep3(StringBuilder sb) {
        // (m>0) ICATE ->  IC
        // (m>0) ATIVE ->
        for (int i = 0; i < STEP3_SUFFIXES.length; i++) {
            if (endsWith(sb, STEP3_SUFFIXES[i])) {
                int stemLen = sb.length() - STEP3_SUFFIXES[i].length();
                int m = getM(sb, stemLen);
                if (m > 0) {
                    sb.setLength(stemLen);
                    sb.append(STEP3_REPLACEMENTS[i]);
                }
                return;
            }
        }

    }

    void stemStep4(StringBuilder sb) {
        // (m>1) AL    ->
        // (m>1) ANCE  ->
        for(String suffix : STEP4_SUFFIXES) {
            if (endsWith(sb, suffix)) {
                int stemLen = sb.length() - suffix.length();
                int m = getM(sb, stemLen);
                if (m > 1) {
                    if (suffix.equals("ion")) {
                        if (sb.charAt(stemLen - 1) == 's' || sb.charAt(stemLen - 1) == 't') {
                            sb.setLength(stemLen);
                        }
                    } else {
                        sb.setLength(stemLen);
                    }
                }
                return;
            }
        }
    }

    void stemStep5a(StringBuilder sb) {
        if (endsWith(sb, "e")) {
            int stemLen = sb.length() - 1;
            int m = getM(sb, stemLen);
            // (m>1) E     ->
            if (m > 1) {
                sb.setLength(stemLen);
                return;
            }
            // (m=1 and not *o) E ->
            if (m == 1 && !isStarO(sb, stemLen)) {
                sb.setLength(stemLen);
            }
        }
    }

    void stemStep5b(StringBuilder sb) {
        // (m > 1 and *d and *L) -> single letter
        int m = getM(sb, sb.length());
        if (m > 1 && endsWith(sb, "ll")) {
            sb.setLength(sb.length() - 1);
        }
    }

    private char getLastDoubleConsonant(StringBuilder sb) {
        if (sb.length() < 2) return 0;
        char lastLetter = sb.charAt(sb.length() - 1);
        char penultimateLetter = sb.charAt(sb.length() - 2);
        if (lastLetter == penultimateLetter && getLetterType((char) 0, lastLetter) == 'c') {
            return lastLetter;
        }
        return 0;
    }

    // *o  - the stem ends cvc, where the second c is not W, X or Y (e.g.
    //                                                              -WIL, -HOP)
    private boolean isStarO(StringBuilder sb, int len) {
        if (len < 3) return false;

        char lastLetter = sb.charAt(len - 1);
        if (lastLetter == 'w' || lastLetter == 'x' || lastLetter == 'y') return false;

        char secondToLastLetter = sb.charAt(len - 2);
        char thirdToLastLetter = sb.charAt(len - 3);
        char fourthToLastLetter = len == 3 ? 0 : sb.charAt(len - 4);
        return getLetterType(secondToLastLetter, lastLetter) == 'c'
                && getLetterType(thirdToLastLetter, secondToLastLetter) == 'v'
                && getLetterType(fourthToLastLetter, thirdToLastLetter) == 'c';
    }

    // m is the number of v -> c transitions in the per-character c/v sequence
    // over sb[0..len). Equivalent to the original "collapse same-type runs,
    // count VC pairs" formulation, without materialising the collapsed string.
    int getM(StringBuilder sb, int len) {
        int m = 0;
        char prevLetter = 0;
        char prevType = 0;
        for (int i = 0; i < len; i++) {
            char c = sb.charAt(i);
            char type = getLetterType(prevLetter, c);
            if (prevType == 'v' && type == 'c') m++;
            prevLetter = c;
            prevType = type;
        }
        return m;
    }

    private boolean containsVowel(StringBuilder sb, int len) {
        char prev = 0;
        for (int i = 0; i < len; i++) {
            char c = sb.charAt(i);
            if (getLetterType(prev, c) == 'v') return true;
            prev = c;
        }
        return false;
    }

    private static boolean endsWith(StringBuilder sb, String suffix) {
        int sufLen = suffix.length();
        int bufLen = sb.length();
        if (bufLen < sufLen) return false;
        int offset = bufLen - sufLen;
        for (int i = 0; i < sufLen; i++) {
            if (sb.charAt(offset + i) != suffix.charAt(i)) return false;
        }
        return true;
    }

    private char getLetterType(char previousLetter, char letter) {
        switch (letter) {
            case 'a':
            case 'e':
            case 'i':
            case 'o':
            case 'u':
                return 'v';
            case 'y':
                if (previousLetter == 0 || getLetterType((char) 0, previousLetter) == 'v') {
                    return 'c';
                }
                return 'v';
            default:
                return 'c';
        }
    }
}

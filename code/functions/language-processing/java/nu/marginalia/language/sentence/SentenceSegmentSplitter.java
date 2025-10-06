package nu.marginalia.language.sentence;

import com.google.common.base.CharMatcher;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.language.encoding.UnicodeNormalization;
import nu.marginalia.language.model.LanguageDefinition;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;

import static nu.marginalia.language.WordPatterns.MAX_WORD_LENGTH;

public class SentenceSegmentSplitter {

    private final UnicodeNormalization unicodeNormalization;

    public record SeparatedSentence(String[] words, BitSet separators) { }

    private static final CharMatcher noiseCharacterMatcher = CharMatcher.anyOf("/*-");


    SentenceSegmentSplitter(LanguageDefinition languageDefinition) {
        this.unicodeNormalization = languageDefinition.unicodeNormalization();
    }

    /** Split a sentence into words and separators.
     *
     * @param segment The sentence to split
     * @return A list of words and separators
     */
    public SeparatedSentence splitSegment(String segment, int maxLength) {
        String flatSegment = unicodeNormalization.flattenUnicode(segment);

        Matcher matcher = unicodeNormalization.wordBreakPattern().matcher(flatSegment);

        List<String> words = new ArrayList<>(flatSegment.length()/6);
        TIntArrayList separators = new TIntArrayList(flatSegment.length()/6);

        int wordStart = 0;
        while (wordStart <= flatSegment.length()) {
            if (!matcher.find(wordStart)) {
                words.add(flatSegment.substring(wordStart));
                separators.add(WordSeparator.SPACE);
                break;
            }

            if (wordStart != matcher.start()) {
                String word = flatSegment.substring(wordStart, matcher.start());
                String space = flatSegment.substring(matcher.start(), matcher.end());

                words.add(word);

                if (space.isBlank()) {
                    separators.add(WordSeparator.SPACE);
                }
                else {
                    separators.add(WordSeparator.COMMA);
                }

            }
            wordStart = matcher.end();
        }

        List<String> ret = new ArrayList<>(words.size());
        BitSet seps = new BitSet(separators.size());

        String[] parts = words.toArray(String[]::new);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isBlank())
                continue;
            if (parts[i].length() >= MAX_WORD_LENGTH)
                continue;
            if (noiseCharacterMatcher.matchesAllOf(parts[i]))
                continue;

            ret.add(parts[i]);
            if (separators.getQuick(i) > 0) {
                seps.set(i);
            }
        }

        for (int i = 0; i < ret.size(); i++) {
            String part  = ret.get(i);

            if (part.startsWith("<") && part.endsWith(">") && part.length() > 2) {
                ret.set(i, part.substring(1, part.length() - 1));
            }

            if (part.startsWith("'") && part.length() > 1) {
                ret.set(i, part.substring(1));
            }
            if (part.endsWith("'") && part.length() > 1) {
                ret.set(i, part.substring(0, part.length()-1));
            }

            while (part.endsWith(".")) {
                part = part.substring(0, part.length()-1);
                ret.set(i, part);
            }
        }

        if (ret.size() > maxLength) {
            ret.subList(maxLength, ret.size()).clear();
            seps = seps.get(0, maxLength);
        }

        return new SeparatedSentence(
                ret.toArray(String[]::new),
                seps
        );
    }


    public static final class WordSeparator {
        public static final int COMMA = 0;
        public static final int SPACE = 1;
    }
}

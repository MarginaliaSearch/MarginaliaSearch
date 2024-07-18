package nu.marginalia.language.sentence;

import com.google.common.base.CharMatcher;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.language.encoding.AsciiFlattener;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Pattern;

import static nu.marginalia.language.WordPatterns.MAX_WORD_LENGTH;

public class SentenceSegmentSplitter {

    public record SeparatedSentence(String[] words, BitSet separators) { }

    private static final CharMatcher noiseCharacterMatcher = CharMatcher.anyOf("/*-");

    private static final Pattern wordBreakPattern;

    static {
        if (Boolean.getBoolean("system.noFlattenUnicode")) {
            // If we don't flatten unicode, we split words on whitespace and punctuation.
            wordBreakPattern = Pattern.compile("(\\s+|[|]|([.,;\\-]+(\\s+|$)))");
        }
        else {
            // If we flatten unicode, we do this...
            // FIXME: This can almost definitely be cleaned up and simplified.
            wordBreakPattern = Pattern.compile("([^/_#@.a-zA-Z'+\\-0-9\\u00C0-\\u00D6\\u00D8-\\u00f6\\u00f8-\\u00ff]+)|[|]|(\\.(\\s+|$))");
        }
    }

    /** Split a sentence into words and separators.
     *
     * @param segment The sentence to split
     * @return A list of words and separators
     */
    public static SeparatedSentence splitSegment(String segment, int maxLength) {
        String flatSegment = AsciiFlattener.flattenUnicode(segment);

        var matcher = wordBreakPattern.matcher(flatSegment);

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

package nu.marginalia.language.sentence;

import com.google.common.base.CharMatcher;
import gnu.trove.list.array.TIntArrayList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.language.encoding.AsciiFlattener;
import nu.marginalia.language.model.WordSeparator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static nu.marginalia.language.WordPatterns.*;

public class SentenceSegmentSplitter {

    @AllArgsConstructor
    @Getter
    public static class SeparatedSentence {
        String[] words;
        int[] separators;
    }

    private static final CharMatcher noiseCharacterMatcher = CharMatcher.anyOf("/*-");
    private static final Pattern wordBreakPattern = Pattern.compile("([^_#@.a-zA-Z'+\\-0-9\\u00C0-\\u00D6\\u00D8-\\u00f6\\u00f8-\\u00ff]+)|[|]|(\\.(\\s+|$))");

    public static SeparatedSentence splitSegment(String segment) {
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
        TIntArrayList seps = new TIntArrayList(words.size());

        String[] parts = words.toArray(String[]::new);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isBlank())
                continue;
            if (parts[i].length() >= MAX_WORD_LENGTH)
                continue;
            if (noiseCharacterMatcher.matchesAllOf(parts[i]))
                continue;

            ret.add(parts[i]);
            seps.add(separators.getQuick(i));
        }

        for (int i = 0; i < ret.size(); i++) {
            String part  = ret.get(i);

            if (part.startsWith("'") && part.length() > 1) {
                ret.set(i, part.substring(1));
            }
            if (part.endsWith("'") && part.length() > 1) {
                ret.set(i, part.substring(0, part.length()-1));
            }
        }

        return new SeparatedSentence(
                ret.toArray(String[]::new),
                seps.toArray()
        );
    }


}

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

        String[] parts = words.toArray(String[]::new);
        int length = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isBlank() || parts[i].length() >= MAX_WORD_LENGTH || noiseCharacterMatcher.matchesAllOf(parts[i])) {
                parts[i] = null;
            }
            else {
                length++;
            }
        }

        String[] ret = new String[length];
        int[] seps = new int[length];
        for (int i = 0, j=0; i < parts.length; i++) {
            if (parts[i] != null) {
                seps[j] = separators.getQuick(i);
                ret[j++] = parts[i];
            }
        }

        for (int i = 0; i < ret.length; i++) {
            if (ret[i].startsWith("'") && ret[i].length() > 1) { ret[i] = ret[i].substring(1); }
            if (ret[i].endsWith("'") && ret[i].length() > 1) { ret[i] = ret[i].substring(0, ret[i].length()-1); }
        }

        return new SeparatedSentence(
                ret.toArray(String[]::new),
                seps.toArray()
        );
    }


}

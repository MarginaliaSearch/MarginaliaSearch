package nu.marginalia.index.results.snippet;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.ArrayList;
import java.util.List;

public class SnippetTokenizer {

    public static List<Sentence> splitSentences(String text) {
        List<Sentence> sentences = new ArrayList<>();

        int tokensTotal = 0;
        final int length = text.length();

        for (int lineStart = 0; lineStart < length; ) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = length;
            }

            int charStart = lineStart;
            int charEnd = lineEnd;

            while (charStart < charEnd && isTokenBoundary(text.charAt(charStart))) charStart++;
            while (charEnd > charStart && isTokenBoundary(text.charAt(charEnd - 1))) charEnd--;

            int lineTokens = countTokens(text, charStart, charEnd);
            if (lineTokens > 0) {
                sentences.add(new Sentence(sentences.size(),
                        tokensTotal, tokensTotal + lineTokens,
                        charStart,
                        charEnd));
                tokensTotal += lineTokens;
            }

            lineStart = lineEnd + 1;
        }

        return sentences;
    }

    private static int countTokens(String text, int charStart, int charEnd) {
        int count = 0;
        boolean previousBoundary = true;

        for (int i = charStart; i < charEnd; i++) {
            var isBoundary = isTokenBoundary(text.charAt(i));

            if (isBoundary && !previousBoundary) { // rising edge for boundary
                count++;
            }

            previousBoundary = isBoundary;
        }

        // We're counting the fence posts by the gaps between them
        return count + 1;
    }

    private static boolean isTokenBoundary(char c) {
        return c == ' ' || c == ',';
    }

    public record Sentence(int sentenceIdx,
                            int firstTokenIdx,
                            int lastTokenIdx, // exclusive
                            int charStart,
                            int charEnd) // exclusive
    {
        int length() {
            return charEnd - charStart;
        }

        public TokenBounds tokenize(String text) {
            TokenBounds tokens = new TokenBounds();

            for (int i = charStart(); i < charEnd();) {
                while (i < charEnd() && isTokenBoundary(text.charAt(i))) i++;
                if (i == charEnd())
                    break;

                int start = i;
                while (i < charEnd() && !isTokenBoundary(text.charAt(i))) i++;
                tokens.add(start, i);
            }

            return tokens;
        }
    }

    public static class TokenBounds {
        private final IntArrayList starts = new IntArrayList();
        private final IntArrayList ends = new IntArrayList();

        public int length() {
            return starts.size();
        }
        public int start(int i) {
            return starts.getInt(i);
        }
        public int end(int i) {
            return ends.getInt(i);
        }

        void add(int start, int end) {
            starts.add(start);
            ends.add(end);
        }
    }

}

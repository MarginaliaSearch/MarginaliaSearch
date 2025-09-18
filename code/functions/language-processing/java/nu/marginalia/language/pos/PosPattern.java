package nu.marginalia.language.pos;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.WordSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

public class PosPattern {
    public final LongArrayList pattern = new LongArrayList();
    private static final Logger logger = LoggerFactory.getLogger(PosPattern.class);

    public long[] toArray() {
        return pattern.toLongArray();
    }

    public int size() {
        return pattern.size();
    }

    public PosPattern(PosTagger posTagger, String expression) {
        for (List<String> variants : PosTagPatternParser.parse(posTagger, expression)) {
            pattern.add(posTagger.encodeTagNames(variants));
        }

        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("Zero length patterns are not allowed");
        }
    }

    public int matchSentence(DocumentSentence sentence, List<WordSpan> ret) {
        long first = pattern.getLong(0);
        int cnt = 0;

        // Fast case for 1-length patterns
        if (pattern.size() == 1) {
            for (int i = 0; i < sentence.length(); i++) {
                if (0L == (sentence.posTags[i] & first)) continue;
                ret.add(new WordSpan(i, i+1));
                cnt++;
            }

            return cnt;
        }

        pattern:
        for (int i = 0; i <= sentence.length() - pattern.size(); i++) {

            // Start by matching against the beginning of the pattern
            // as a fast path
            if (0L == (sentence.posTags[i] & first)) continue;


            int j;
            for (j = 1; j < pattern.size(); j++) {
                if (0L == (sentence.posTags[i + j] & pattern.getLong(j)))
                    continue pattern;
            }

            // Ensure no commas exist in the sentence except for the last word
            int nextCommaPos = sentence.nextCommaPos(i);
            if (nextCommaPos < i + pattern.size() - 1) {
                // note the i++ in the for loop will also be added here, so we're positioned after the next comma
                // beginning of the next iteration
                i = nextCommaPos;
                continue;
            }

            // Finally add the span
            ret.add(new WordSpan(i, i+j));
            cnt++;
        }

        return cnt;
    }

    public boolean isMatch(DocumentSentence sentence, int pos) {
        if (pos + pattern.size() > sentence.length()) {
            return false;
        }

        long first = pattern.getLong(0);
        if (0 == (sentence.posTags[pos] & first)) return false;
        else if (pattern.size() == 1) return true;

        int nextCommaPos = sentence.nextCommaPos(pos);
        if (nextCommaPos < pos + pattern.size() - 1) {
            return false;
        }

        for (int j = 1; j < pattern.size(); j++) {
            if (0L == (sentence.posTags[pos+j] & pattern.getLong(j)))
                return false;
        }
        return true;
    }

    /** Return a bit set for every position where this pattern matches the tag sequence provided */
    public BitSet matchTagPattern(long[] tags) {
        BitSet bs = new BitSet(tags.length);

        // Fast case for length = 1
        if (pattern.size() == 1) {
            long patternVal = pattern.getLong(0);

            for (int i = 0; i < tags.length; i++) {
                bs.set(i, (patternVal & tags[i]) != 0L);
            }

            return bs;
        }

        pattern:
        for (int i = 0; i <= tags.length - pattern.size(); i++) {
            int j;

            for (j = 0; j < pattern.size(); j++) {
                if (0L == (tags[i+j] & pattern.getLong(j)))
                    continue pattern;
            }

            bs.set(i);
        }

        return bs;
    }
}

class PosTagPatternParser {
    private boolean inverted;
    private boolean inParen;

    private final List<List<String>> variants = new ArrayList<>();
    private final List<String> allTags;

    public PosTagPatternParser(PosTagger posTagger) {
        allTags = Collections.unmodifiableList(posTagger.tags());
    }

    public static List<List<String>> parse(PosTagger posTagger, String expression) {

        PosTagPatternParser patternBuilder = new PosTagPatternParser(posTagger);

        for (String token : tokenize(expression)) {
            switch (token) {
                case "!" -> patternBuilder.invert();
                case "(" -> patternBuilder.parenOpen();
                case ")" -> patternBuilder.parenClose();
                default -> patternBuilder.addToken(token);
            }
        }

        return patternBuilder.variants;
    }

    private static List<String> tokenize(String expression) {
        List<String> tokens = new ArrayList<>();
        int pos = 0;

        while (pos < expression.length()) {
            char c = expression.charAt(pos);
            if ("()!".indexOf(c) >= 0) {
                tokens.add(expression.substring(pos, pos + 1));
                pos++;
            }
            else if (Character.isSpaceChar(c)) {
                pos++;
            }
            else {
                int end =  pos + 1;
                while (end <  expression.length()) {
                    int ce = expression.charAt(end);
                    if ("() ".indexOf(ce) >= 0) {
                        break;
                    }
                    else {
                        end++;
                    }
                }
                tokens.add(expression.substring(pos, end));
                pos = end;
            }
        }

        return tokens;

    }

    public void invert() {
        inverted = true;
    }
    public void parenOpen() {
        inParen = true;
        beginToken();
    }

    public void parenClose() {
       inParen = false;
       inverted = false;
    }

    private void beginToken() {
        variants.add(new ArrayList<>());
        if (inverted)
            variants.getLast().addAll(allTags);
    }

    public void addToken(String token) {
       if (!inParen) beginToken();

       List<String> tokensExpanded;
       if (token.endsWith("*")) {
           String prefix = token.substring(0, token.length() - 1);
           tokensExpanded = allTags.stream().filter(str -> prefix.isEmpty() || str.startsWith(prefix)).toList();
       }
       else {
           tokensExpanded = List.of(token);
       }

       if (inverted) {
           variants.getLast().removeAll(tokensExpanded);
       }
       else {
           variants.getLast().addAll(tokensExpanded);
       }

       if (!inParen) {
           inverted = false;
       }
    }
}
package nu.marginalia.language.model;


import nu.marginalia.language.WordPatterns;
import nu.marginalia.language.sentence.tag.HtmlTag;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;
import java.util.*;

/** Represents a sentence in a document, with POS tags, HTML tags, and other information
 *  about the words in the sentence.
 * */
public class DocumentSentence implements Iterable<DocumentSentence.SentencePos> {

    /** A span of words in a sentence */
    public final String[] wordsLowerCase;
    public final String[] stemmedWords;
    public final long[] posTags;

    /** A set of HTML tags that surround the sentence */
    public final EnumSet<HtmlTag> htmlTags;

    /** A bitset indicating whether the word is a stop word */
    private final BitSet isStopWord;
    private final BitSet includeInStemming;

    /** A bitset indicating whether the word is capitalized */
    private final BitSet isCapitalized;

    /** A bitset indicating whether the word is all caps */
    private final BitSet isAllCaps;

    // Encode whether the words are separated by a comma or a space,
    // where false = COMMA, true = SPACE
    private final BitSet separators;

    public DocumentSentence(BitSet separators,
                            String[] wordsLowerCase,
                            long[] posTags,
                            String[] stemmedWords,
                            EnumSet<HtmlTag> htmlTags,
                            BitSet isCapitalized,
                            BitSet isAllCaps,
                            BitSet includeInStemming
                            )
    {
        this.separators = separators;
        this.wordsLowerCase = wordsLowerCase;
        this.posTags = posTags;
        this.stemmedWords = stemmedWords;
        this.htmlTags = htmlTags;
        this.isCapitalized = isCapitalized;
        this.isAllCaps = isAllCaps;
        this.includeInStemming = includeInStemming;

        isStopWord = new BitSet(wordsLowerCase.length);

        for (int i = 0; i < wordsLowerCase.length; i++) {
            if (WordPatterns.isStopWord(wordsLowerCase[i]))
                isStopWord.set(i);
        }
    }

    public static DocumentSentence ofSynthetic(List<String> lowerCase, List<String> stemmed, EnumSet<HtmlTag> htmlTags) {
        if (lowerCase.size() != stemmed.size()) {
            throw new IllegalArgumentException("Mismatching input lengths");
        }

        // since these will all be the same, we can reuse one instance
        final BitSet emptyBitSet = new BitSet(lowerCase.size());

        return new DocumentSentence(
                emptyBitSet,
                lowerCase.toArray(String[]::new),
                new long[lowerCase.size()],
                stemmed.toArray(String[]::new),
                htmlTags,
                emptyBitSet,
                emptyBitSet,
                emptyBitSet
        );
    }

    public boolean isStopWord(int idx) {
        return isStopWord.get(idx);
    }

    public int length() {
        return wordsLowerCase.length;
    }

    public boolean isCapitalized(int i) {
        return isCapitalized.get(i);
    }
    public boolean isAllCaps(int i) {
        return isAllCaps.get(i);
    }

    public boolean isSeparatorSpace(int i) {
        return separators.get(i);
    }
    public boolean isSeparatorComma(int i) {
        return !separators.get(i);
    }

    /** Returns the position of the next comma in the sentence,
     * or sentence.length() if no remaining commas exist.
     */
    public int nextCommaPos(int pos) {
        int ret = separators.nextClearBit(pos);
        if (ret < 0)
            return separators.length();
        return ret;
    }

    public String constructWordFromSpan(WordSpan span) {
        if (span.size() == 1) {
            return trimJunkCharacters(wordsLowerCase[span.start]);
        }
        else {
            StringJoiner sj = new StringJoiner("_");
            for (int i = span.start; i < span.end; i++) {
                sj.add(wordsLowerCase[i]);
            }
            return trimJunkCharacters(sj.toString());
        }
    }

    public String constructStemmedWordFromSpan(WordSpan span) {
        if (span.size() > 1) {

            StringJoiner sj = new StringJoiner("_");
            for (int i = span.start; i < span.end; i++) {
                if (includeInStemming(i))
                    sj.add(normalizeJoiner(stemmedWords[i]));

            }
            return sj.toString();
        }
        else if (includeInStemming(span.start)) {
            return normalizeJoiner(stemmedWords[span.start]);
        }
        else return "";
    }

    private String trimJunkCharacters(String s) {
        int start = 0;
        int end = s.length();

        for (; start < end; start++) {
            if ("\"'_*".indexOf(s.charAt(start)) < 0)
                break;
        }

        for (; end > start; end--) {
            if ("\"'_*".indexOf(s.charAt(end-1)) < 0)
                break;
        }

        if (start > 0 || end < s.length()) {
            return s.substring(start, end);
        }
        else {
            return s;
        }
    }
    private String normalizeJoiner(String s) {

        if (s.indexOf('+') >= 0) {
            s = s.replace('+', '_');
        }
        if (s.indexOf('.') >= 0) {
            s = s.replace('.', '_');
        }
        if (s.indexOf('-') >= 0) {
            s = s.replace('-', '_');
        }
        return s;
    }

    private boolean includeInStemming(int i) {
        return includeInStemming.get(i);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordsLowerCase.length; i++) {
            sb.append(wordsLowerCase[i]);
            if (posTags.length > 0)
                sb.append('[').append(posTags[i]).append(']');
            if (isSeparatorComma(i)) {
                sb.append(',');
            }
            else {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    @NotNull
    @Override
    public Iterator<SentencePos> iterator() {
        return new Iterator<>() {
            int i = -1;
            @Override
            public boolean hasNext() {
                return i+1 < length();
            }

            @Override
            public SentencePos next() {
                return new SentencePos(++i);
            }
        };
    }

    public class SentencePos {
        public final int pos;

        public SentencePos(int pos) {
            this.pos = pos;
        }

        public String wordLowerCase() { return wordsLowerCase[pos]; }
        public long posTag() { return posTags[pos]; }
        public String stemmed() { return stemmedWords[pos]; }
        public boolean isStopWord() { return DocumentSentence.this.isStopWord(pos); }

        public WordRep rep() {
            return new WordRep(DocumentSentence.this, new WordSpan(pos, pos+1));
        }
    }
}


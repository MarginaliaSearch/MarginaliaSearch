package nu.marginalia.util.language.processing.model;


import nu.marginalia.util.language.WordPatterns;

import java.lang.ref.SoftReference;
import java.util.BitSet;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DocumentSentence {
    public final String originalSentence;
    public final String[] words;
    public final int[] separators;
    public final String[] wordsLowerCase;
    public final String[] posTags;
    public final String[] stemmedWords;

    private final BitSet isStopWord;

    public SoftReference<WordSpan[]> keywords;

    public DocumentSentence(String originalSentence, String[] words, int[] separators, String[] wordsLowerCase, String[] posTags, String[] stemmedWords) {
        this.originalSentence = originalSentence;
        this.words = words;
        this.separators = separators;
        this.wordsLowerCase = wordsLowerCase;
        this.posTags = posTags;
        this.stemmedWords = stemmedWords;

        isStopWord = new BitSet(words.length);

        for (int i = 0; i < words.length; i++) {
            if (WordPatterns.isStopWord(words[i]))
                isStopWord.set(i);
        }
    }

    public boolean isStopWord(int idx) {
        return isStopWord.get(idx);
    }
    public void setIsStopWord(int idx, boolean val) {
        if (val)
            isStopWord.set(idx);
        else
            isStopWord.clear();
    }
    public int length() {
        return words.length;
    }

    private final static Pattern trailingJunkPattern = Pattern.compile("(^[\"'_*]+|[_*'\"]+$)");
    private final static Pattern joinerPattern = Pattern.compile("[-+.]+");

    public String constructWordFromSpan(WordSpan span) {
        StringJoiner sj = new StringJoiner("_");
        for (int i = span.start; i < span.end; i++) {
            sj.add(wordsLowerCase[i]);
        }

        return trailingJunkPattern.matcher(sj.toString()).replaceAll("");
    }

    public String constructStemmedWordFromSpan(WordSpan span) {
        StringJoiner sj = new StringJoiner("_");
        for (int i = span.start; i < span.end; i++) {
            if (includeInStemming(i))
                sj.add(joinerPattern.matcher(stemmedWords[i]).replaceAll("_"));

        }
        return sj.toString();
    }

    private boolean includeInStemming(int i) {
        if (posTags[i].equals("IN") || posTags[i].equals("TO") || posTags[i].equals("CC") || posTags[i].equals("DT")) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return IntStream.range(0, length()).mapToObj(i -> String.format("%s[%s]", words[i], posTags[i])).collect(Collectors.joining(" "));
    }
}

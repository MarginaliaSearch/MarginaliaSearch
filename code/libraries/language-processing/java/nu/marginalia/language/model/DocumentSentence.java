package nu.marginalia.language.model;


import nu.marginalia.language.WordPatterns;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;
import java.util.BitSet;
import java.util.Iterator;
import java.util.StringJoiner;

public class DocumentSentence implements Iterable<DocumentSentence.SentencePos>{
    public final String originalSentence;
    public final String[] words;
    public final int[] separators;
    public final String[] wordsLowerCase;
    public final String[] posTags;
    public final String[] stemmedWords;
    public final String[] ngrams;
    public final String[] ngramStemmed;

    private final BitSet isStopWord;


    public SoftReference<WordSpan[]> keywords;

    public DocumentSentence(String originalSentence,
                            String[] words,
                            int[] separators,
                            String[] wordsLowerCase,
                            String[] posTags,
                            String[] stemmedWords,
                            String[] ngrams,
                            String[] ngramsStemmed
                            )
    {
        this.originalSentence = originalSentence;
        this.words = words;
        this.separators = separators;
        this.wordsLowerCase = wordsLowerCase;
        this.posTags = posTags;
        this.stemmedWords = stemmedWords;

        isStopWord = new BitSet(words.length);

        this.ngrams = ngrams;
        this.ngramStemmed = ngramsStemmed;

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
        if (posTags[i].equals("IN") || posTags[i].equals("TO") || posTags[i].equals("CC") || posTags[i].equals("DT")) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            sb.append(words[i]).append('[').append(posTags[i]).append(']');
            if (separators[i] == WordSeparator.COMMA) {
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

        public String word() { return words[pos]; }
        public String wordLowerCase() { return wordsLowerCase[pos]; }
        public String posTag() { return posTags[pos]; }
        public String stemmed() { return stemmedWords[pos]; }
        public int separator() { return separators[pos]; }
        public boolean isStopWord() { return DocumentSentence.this.isStopWord(pos); }

        public WordRep rep() {
            return new WordRep(DocumentSentence.this, new WordSpan(pos, pos+1));
        }
    }
}


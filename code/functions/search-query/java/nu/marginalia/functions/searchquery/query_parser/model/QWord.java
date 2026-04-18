package nu.marginalia.functions.searchquery.query_parser.model;

import ca.rmen.porterstemmer.PorterStemmer;

import java.util.Objects;

public record QWord(
        int ord,
        boolean variant,
        String stemmed,
        String word,
        String original)
{

    // These are special words that are not in the input, but are added to the graph,
    // note the space around the ^ and $, to avoid collisions with real words
    private static final String BEG_MARKER = " ^ ";
    private static final String END_MARKER = " $ ";

    private static final PorterStemmer ps = new PorterStemmer();

    public boolean isBeg() {
        return word.equals(BEG_MARKER);
    }

    public boolean isEnd() {
        return word.equals(END_MARKER);
    }

    public static QWord beg() {
        return new QWord(Integer.MIN_VALUE, false, BEG_MARKER, BEG_MARKER, BEG_MARKER);
    }

    public static QWord end() {
        return new QWord(Integer.MAX_VALUE, false, END_MARKER, END_MARKER, END_MARKER);
    }

    public boolean isOriginal() {
        return !variant;
    }

    public QWord(int ord, String word) {
        this(ord, false, ps.stemWord(word), word, word);
    }

    public QWord(int ord, QWord original, String word) {
        this(ord, true, ps.stemWord(word), word, original.original);
    }

    public String toString() {
        return "q{" + word + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QWord qWord = (QWord) o;
        return variant == qWord.variant && Objects.equals(word, qWord.word) && Objects.equals(stemmed, qWord.stemmed) && Objects.equals(isOriginal(), qWord.isOriginal());
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(variant);
        result = 31 * result + Objects.hashCode(stemmed);
        result = 31 * result + Objects.hashCode(word);
        result = 31 * result + Objects.hashCode(isOriginal());
        return result;
    }
}

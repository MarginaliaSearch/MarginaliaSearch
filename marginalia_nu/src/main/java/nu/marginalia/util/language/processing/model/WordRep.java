package nu.marginalia.util.language.processing.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@AllArgsConstructor @Getter
public class WordRep implements Comparable<WordRep> {

    public WordRep(DocumentSentence sent, WordSpan span) {
        word = sent.constructWordFromSpan(span);
        stemmed = sent.constructStemmedWordFromSpan(span);
        length = span.end - span.start;

        hashCode = Objects.hash(word);
    }

    public final int length;
    public final String word;
    public final String stemmed;
    private final int hashCode;

    @Override
    public int compareTo(@NotNull WordRep o) {
        return word.compareTo(o.word);
    }

    @Override
    public String toString() {
        return word;
    }

    public int hashCode() {
        return hashCode;
    }

    public boolean equals(Object other) {
        if (other == this) return true;
        if (other instanceof WordRep wr) {
            return Objects.equals(wr.word, word);
        }
        return false;
    }
}

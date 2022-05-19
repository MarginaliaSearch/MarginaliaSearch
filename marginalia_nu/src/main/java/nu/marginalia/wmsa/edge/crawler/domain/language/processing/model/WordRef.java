package nu.marginalia.wmsa.edge.crawler.domain.language.processing.model;

import lombok.AllArgsConstructor;

import java.util.Objects;
import java.util.Optional;

@AllArgsConstructor
public class WordRef {
    public final int sentenceIndex;
    public final int wordIndex;

    public String getWord(DocumentLanguageData dld) {
        return dld.sentences[sentenceIndex].words[wordIndex];
    }

    public String getWordStemmed(DocumentLanguageData dld) {
        return dld.sentences[sentenceIndex].stemmedWords[wordIndex];
    }

    public Optional<WordRef> next(DocumentLanguageData dld) {
        if (wordIndex + 1 < dld.sentences[sentenceIndex].length()) {
            return Optional.of(new WordRef(sentenceIndex, wordIndex+1));
        }
        return Optional.empty();
    }
    public Optional<WordRef> prev() {
        if (wordIndex - 1 >= 0) {
            return Optional.of(new WordRef(sentenceIndex, wordIndex-1));
        }
        return Optional.empty();
    }

    @Override
    public int hashCode() {
        return Objects.hash(sentenceIndex, wordIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WordRef wordRef = (WordRef) o;
        return sentenceIndex == wordRef.sentenceIndex && wordIndex == wordRef.wordIndex;
    }
}

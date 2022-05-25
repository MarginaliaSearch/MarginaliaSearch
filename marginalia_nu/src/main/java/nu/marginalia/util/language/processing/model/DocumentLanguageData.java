package nu.marginalia.util.language.processing.model;

import gnu.trove.map.hash.TObjectIntHashMap;
import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.stream.Stream;

@AllArgsConstructor
public class DocumentLanguageData {
    public final DocumentSentence[] sentences;
    public final DocumentSentence[] titleSentences;
    public final TObjectIntHashMap<String> wordCount;

    public int totalNumWords() {
        int ret = 0;
        for (int i = 0; i < sentences.length; i++) {
            ret += sentences[i].length();
        }
        return ret;
    }

    public Stream<String> streamLowerCase() {
        return Arrays.stream(sentences).map(sent -> sent.wordsLowerCase).flatMap(Arrays::stream);
    }

    public Stream<String> stream() {
        return Arrays.stream(sentences).map(sent -> sent.words).flatMap(Arrays::stream);
    }
}

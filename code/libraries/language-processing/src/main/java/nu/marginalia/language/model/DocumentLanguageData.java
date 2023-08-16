package nu.marginalia.language.model;

import gnu.trove.map.hash.TObjectIntHashMap;
import lombok.AllArgsConstructor;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.lsh.EasyLSH;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * @see SentenceExtractor
 */
@AllArgsConstructor
public class DocumentLanguageData {
    public final DocumentSentence[] sentences;
    public final DocumentSentence[] titleSentences;
    public final TObjectIntHashMap<String> wordCount;
    public final String text;

    /** for test convenience */
    public static DocumentLanguageData empty() {
        return new DocumentLanguageData(
                new DocumentSentence[0],
                new DocumentSentence[0],
                new TObjectIntHashMap<>(),
                ""
        );
    }

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

    public long localitySensitiveHashCode() {
        var hash = new EasyLSH();

        for (var sent : sentences) {
            for (var word : sent) {
                hash.addUnordered(word.word());
            }
        }
        return hash.get();
    }
}

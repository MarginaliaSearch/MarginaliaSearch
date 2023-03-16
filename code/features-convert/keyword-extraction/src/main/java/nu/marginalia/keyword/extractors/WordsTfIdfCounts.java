package nu.marginalia.keyword.extractors;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.keyword.WordReps;
import nu.marginalia.language.WordPatterns;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.keyword.KeywordExtractor;
import nu.marginalia.language.model.WordSpan;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

import static java.lang.Math.max;

/** Extract counts and TF-IDF for the words in the document,
 * keep track of high-scoring words for flagging
 */
public class WordsTfIdfCounts implements WordReps {
    private final TermFrequencyDict dict;
    private final double docCount;

    private final Object2IntOpenHashMap<String> tfIdf;
    private final Set<WordRep> tfIdfHigh;

    public WordsTfIdfCounts(TermFrequencyDict dict,
                            KeywordExtractor keywordExtractor,
                            DocumentLanguageData dld) {
        this.dict = dict;
        this.docCount = dict.docCount();

        this.tfIdf =  new Object2IntOpenHashMap<>(10_000);

        var counts = getCounts(keywordExtractor, dld);
        int maxVal = maxValue(counts);
        Set<String> highTfIdfInstances = new HashSet<>();

        counts.forEach((key, cnt) -> {
            int value = getTermValue(key, cnt, maxVal);

            tfIdf.put(key, value);
            if (cnt > 1 && value > 100) {
                highTfIdfInstances.add(key);
            }
        });

        // Collect words with a high TF-IDF so that they can be marked with a bit flag

        tfIdfHigh = new HashSet<>(100);
        for (var sent : dld.sentences) {
            var keywords = keywordExtractor.getKeywordsFromSentence(sent);
            for (var span : keywords) {
                if (highTfIdfInstances.contains(spanToStemmed(sent, span))) {
                    tfIdfHigh.add(new WordRep(sent, span));
                }
            }
        }

    }

    private Object2IntOpenHashMap<String> getCounts(KeywordExtractor keywordExtractor, DocumentLanguageData dld) {
        Object2IntOpenHashMap<String> counts = new Object2IntOpenHashMap<>(10_000, 0.7f);
        counts.defaultReturnValue(0);

        for (var sent : dld.sentences) {
            var keywords = keywordExtractor.getKeywordsFromSentence(sent);
            for (var span : keywords) {

                if (span.size() == 1
                        && WordPatterns.isStopWord(sent.words[span.start]))
                {
                    continue;
                }

                counts.addTo(spanToStemmed(sent, span), 1);
            }
        }

        return counts;
    }

    private String spanToStemmed(DocumentSentence sentence, WordSpan span) {
        if (span.size() == 1)
            return sentence.stemmedWords[span.start];

        StringBuilder builder = new StringBuilder();
        for (int i = span.start; i < span.end; i++) {
            if (!builder.isEmpty())
                builder.append('_');
            builder.append(sentence.stemmedWords[i]);
        }
        return builder.toString();

    }

    public int getTfIdf(String stemmed) {
        return tfIdf.getOrDefault(stemmed, 0);
    }

    @Override
    public Collection<WordRep> getReps() {
        return tfIdfHigh;
    }

    private int maxValue(Object2IntOpenHashMap<?> map) {
        int maxC = 0;

        for (int c : map.values()) {
            maxC = max(c, maxC);
        }

        return maxC;
    }

    public int getTermValue(String key, int count, double maxValue) {
        if (key.indexOf('_') >= 0) {
            String[] parts = StringUtils.split(key, '_');
            double totalValue = 0.;
            for (String part : parts) {
                totalValue += value(part, count, maxValue);
            }
            return normalizeValue(totalValue / parts.length);
        }
        else {
            return normalizeValue(value(key, count, maxValue));
        }
    }

    int normalizeValue(double v) {
        return (int)(-v*75);
    }

    double value(String key, double value, double maxValue) {
        double freq = dict.getTermFreqStemmed(key);
        if (freq < 1) {
            freq = 1;
        }
        return (0.1 + 0.9*value/maxValue) * Math.log(freq/docCount);
    }

}

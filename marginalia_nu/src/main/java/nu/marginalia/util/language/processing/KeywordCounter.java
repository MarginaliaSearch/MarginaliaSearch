package nu.marginalia.util.language.processing;

import com.github.jknack.handlebars.internal.lang3.StringUtils;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.KeywordMetadata;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static java.lang.Math.max;

public class KeywordCounter {
    private final KeywordExtractor keywordExtractor;
    private final TermFrequencyDict dict;
    private final double docCount;

    public KeywordCounter(TermFrequencyDict dict, KeywordExtractor keywordExtractor) {
        this.dict = dict;
        this.keywordExtractor = keywordExtractor;
        this.docCount = dict.docCount();
    }

    public List<WordRep> countHisto(KeywordMetadata keywordMetadata, DocumentLanguageData dld) {
        Object2IntOpenHashMap<String> counts = new Object2IntOpenHashMap<>(10_000, 0.7f);
        HashMap<String, HashSet<WordRep>> instances = new HashMap<>(15000);


        for (var sent : dld.sentences) {
            var keywords = keywordExtractor.getKeywordsFromSentence(sent);
            for (var span : keywords) {

                if (span.size() == 1 && WordPatterns.isStopWord(sent.words[span.start])) {
                    continue;
                }

                var rep = new WordRep(sent, span);

                counts.mergeInt(rep.stemmed, 1, Integer::sum);

                var instanceSet = instances.computeIfAbsent(rep.stemmed, k -> new HashSet<>(500));
                if (instanceSet.size() < 250) {
                    instanceSet.add(rep);
                }
            }
        }

        HashMap<String, WordFrequencyData> tfIdf = keywordMetadata.wordsTfIdf();
        List<WordRep> tfIdfHigh = new ArrayList<>();

        int maxVal = maxValue(counts);


        counts.forEach((key, cnt) -> {
            int value = getTermValue(key, cnt, maxVal);

            tfIdf.put(key, new WordFrequencyData(cnt, value));

            if (cnt > 1 && value > 100) {
                tfIdfHigh.addAll(instances.get(key));
            }
        });

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

    public record WordFrequencyData(int count, int tfIdfNormalized) { }
}

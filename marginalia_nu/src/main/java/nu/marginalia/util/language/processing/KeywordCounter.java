package nu.marginalia.util.language.processing;

import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class KeywordCounter {
    private final KeywordExtractor keywordExtractor;
    private final NGramDict dict;

    public KeywordCounter(NGramDict dict, KeywordExtractor keywordExtractor) {
        this.dict = dict;
        this.keywordExtractor = keywordExtractor;
    }

    public WordHistogram countHisto(DocumentLanguageData dld) {
        HashMap<String, Double> counts = new HashMap<>(1000);
        HashMap<String, HashSet<WordRep>> instances = new HashMap<>(1000);


        for (var sent : dld.sentences) {
            var keywords = keywordExtractor.getKeywordsFromSentence(sent);
            for (var span : keywords) {

                String stemmed = sent.constructStemmedWordFromSpan(span);

                counts.merge(stemmed, 1., Double::sum);
                instances.computeIfAbsent(stemmed, k -> new HashSet<>()).add(new WordRep(sent, span));
            }
        }

        double maxC = counts.values().stream().mapToDouble(Double::valueOf).max().orElse(1);

        Set<WordRep> h5 = new HashSet<>();
        Set<WordRep> h10 = new HashSet<>();
        Set<WordRep> h15 = new HashSet<>();

        for (var entry : counts.entrySet()) {
            double value = getTermValue(entry, maxC);
            Set<WordRep> histogram;
            if (value < -3) histogram = h15;
            else if (value < -2) histogram = h10;
            else if (value < -1) histogram = h5;
            else continue;

            histogram.addAll(instances.get(entry.getKey()));
        }

        return new WordHistogram(h5, h10, h15);
    }

    private static final Pattern separator = Pattern.compile("_");

    public double getTermValue(Map.Entry<String, Double> e, double maxValue) {
        String[] parts = separator.split(e.getKey());
        double totalValue = 0.;
        for (String part : parts) {
            totalValue += value(part, e.getValue(), maxValue);
        }
        return totalValue / parts.length;
    }

    double value(String key, double value, double maxValue) {
        double freq = dict.getTermFreqStemmed(key);
        if (freq < 1) {
            freq = 10;
        }
        return (0.1 + 0.9*value/maxValue) * Math.log((1.1+freq)/11820118.);
    }

    public record WordHistogram(Set<WordRep> lower, Set<WordRep> mid, Set<WordRep> top) { }
}

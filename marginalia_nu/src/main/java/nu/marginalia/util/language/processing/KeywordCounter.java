package nu.marginalia.util.language.processing;

import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class KeywordCounter {
    private final KeywordExtractor keywordExtractor;
    private final TermFrequencyDict dict;
    private final double docCount;

    public KeywordCounter(TermFrequencyDict dict, KeywordExtractor keywordExtractor) {
        this.dict = dict;
        this.keywordExtractor = keywordExtractor;
        this.docCount = (double) dict.docCount();
    }

    public WordHistogram countHisto(DocumentLanguageData dld) {
        HashMap<String, Integer> counts = new HashMap<>(15000);
        HashMap<String, HashSet<WordRep>> instances = new HashMap<>(15000);


        for (var sent : dld.sentences) {
            var keywords = keywordExtractor.getKeywordsFromSentence(sent);
            for (var span : keywords) {
                if (span.size() == 1 &&
                        WordPatterns.isStopWord(sent.words[span.start]))
                    continue;

                String stemmed = sent.constructStemmedWordFromSpan(span);

                counts.merge(stemmed, 1, Integer::sum);
                instances.computeIfAbsent(stemmed, k -> new HashSet<>(500)).add(new WordRep(sent, span));
            }
        }

        double maxC = counts.values().stream().mapToDouble(Double::valueOf).max().orElse(1);

        Set<WordRep> h5 = new HashSet<>(2500);
        Set<WordRep> h10 = new HashSet<>(500);
        Set<WordRep> h15 = new HashSet<>(500);

        int doubleWordCount = 0;

        for (var entry : counts.entrySet()) {
            double value = getTermValue(entry, maxC);

            double avgCnt = entry.getValue();
            String wordStemmed = entry.getKey();

            Set<WordRep> histogram;
            if (value < -3 && avgCnt>1) histogram = h15;
            else if (value < -1.75 && avgCnt>1) histogram = h10;
            else if (value < -1 &&
                    (!wordStemmed.contains("_") || doubleWordCount++ < 50))
                histogram = h5;
            else continue;

            histogram.addAll(instances.get(wordStemmed));
        }
        return new WordHistogram(h5, h10, h15);
    }

    private static final Pattern separator = Pattern.compile("_");

    public double getTermValue(Map.Entry<String, Integer> e, double maxValue) {
        String key = e.getKey();
        if (key.contains("_")) {
            String[] parts = separator.split(e.getKey());
            double totalValue = 0.;
            for (String part : parts) {
                totalValue += value(part, e.getValue(), maxValue);
            }
            return totalValue / parts.length;
        }
        else {
            return value(key, e.getValue(), maxValue);
        }
    }

    double value(String key, double value, double maxValue) {
        double freq = dict.getTermFreqStemmed(key);
        if (freq < 1) {
            freq = 1;
        }
        return (0.1 + 0.9*value/maxValue) * Math.log(freq/docCount);
    }

    public record WordHistogram(Set<WordRep> lower, Set<WordRep> mid, Set<WordRep> top) { }
}

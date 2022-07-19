package nu.marginalia.util.language.processing;

import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class KeywordCounter {
    private final KeywordExtractor keywordExtractor;
    private final NGramDict dict;

    public KeywordCounter(NGramDict dict, KeywordExtractor keywordExtractor) {
        this.dict = dict;
        this.keywordExtractor = keywordExtractor;
    }

    public List<WordRep> count(DocumentLanguageData dld) {
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

        return counts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .sorted(Comparator.comparing(this::getTermValue))
                .map(Map.Entry::getKey)
                .flatMap(w -> instances.get(w).stream())
                .filter(w -> w.word.length() > 1)
                .limit(150)
                .collect(Collectors.toList());
    }

    private static final Pattern separator = Pattern.compile("_");

    public double getTermValue(Map.Entry<String, Double> e) {
        String[] parts = separator.split(e.getKey());
        double totalValue = 0.;
        for (String part : parts) {
            totalValue += value(part, e.getValue());
        }
        return totalValue / Math.sqrt(parts.length);
    }

    double value(String key, double value) {
        double freq = dict.getTermFreqStemmed(key);
        if (freq < 1) {
            freq = 10;
        }
        return (1+Math.log(value)) * Math.log((1.1+freq)/11820118.);
    }


}

package nu.marginalia.wmsa.edge.crawler.domain.language.processing;

import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.DocumentLanguageData;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.DocumentSentence;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.WordRep;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LongNameCounter {
    private final KeywordExtractor keywordExtractor;

    private final NGramDict dict;
    public LongNameCounter(NGramDict dict, KeywordExtractor keywordExtractor) {
        this.dict = dict;
        this.keywordExtractor = keywordExtractor;
    }

    public List<WordRep> count(DocumentLanguageData dld) {
        HashMap<String, Double> counts = new HashMap<>(1000);
        HashMap<String, HashSet<WordRep>> instances = new HashMap<>(1000);

        for (int i = 0; i < dld.sentences.length; i++) {
            DocumentSentence sent = dld.sentences[i];
            var keywords = keywordExtractor.getNamesStrict(sent);
            for (var span : keywords) {
                var stemmed = sent.constructStemmedWordFromSpan(span);
                counts.merge(stemmed, 1., Double::sum);
                instances.computeIfAbsent(stemmed, k -> new HashSet<>()).add(new WordRep(sent, span));
            }
        }

        return counts.entrySet().stream().filter(e -> termSize(e.getKey()) > 1)
                .sorted(Comparator.comparing(this::getTermValue))
                .limit(Math.min(50, counts.size()/3))
                .map(Map.Entry::getKey)
                .flatMap(w -> instances.get(w).stream()).collect(Collectors.toList());
    }

    int termSize(String word) {
        return 1 + (int) word.chars().filter(c -> c == '_').count();
    }


    final Pattern separator = Pattern.compile("_");

    public double getTermValue(Map.Entry<String, Double> e) {
        String[] parts = separator.split(e.getKey());
        double totalValue = 0.;
        for (String part : parts) {
            totalValue += value(part, e.getValue());
        }
        return totalValue / Math.sqrt(parts.length);
    }

    double value(String key, double value) {
        return (1+Math.log(value)) * Math.log((1.+dict.getTermFreq(key))/11820118.);
    }


}

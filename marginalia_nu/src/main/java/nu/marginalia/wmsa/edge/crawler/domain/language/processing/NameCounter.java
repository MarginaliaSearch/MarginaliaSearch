package nu.marginalia.wmsa.edge.crawler.domain.language.processing;

import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.DocumentLanguageData;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.DocumentSentence;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.WordRep;

import java.util.*;
import java.util.stream.Collectors;

public class NameCounter {
    private final KeywordExtractor keywordExtractor;

    public NameCounter(KeywordExtractor keywordExtractor) {
        this.keywordExtractor = keywordExtractor;
    }

    public List<WordRep> count(DocumentLanguageData dld, int minCount) {
        HashMap<String, Double> counts = new HashMap<>(1000);
        HashMap<String, HashSet<WordRep>> instances = new HashMap<>(1000);

        for (int i = 0; i < dld.sentences.length; i++) {
            DocumentSentence sent = dld.sentences[i];
            var keywords = keywordExtractor.getNames(sent);
            for (var span : keywords) {
                var stemmed = sent.constructStemmedWordFromSpan(span);

                counts.merge(stemmed, 1., Double::sum);
                instances.computeIfAbsent(stemmed, k -> new HashSet<>()).add(new WordRep(sent, span));
            }
        }

        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= minCount)
                .sorted(Comparator.comparing(e -> -e.getValue()))
                .limit(150)
                .map(Map.Entry::getKey)
                .flatMap(w -> instances.get(w).stream()).collect(Collectors.toList());
    }

}

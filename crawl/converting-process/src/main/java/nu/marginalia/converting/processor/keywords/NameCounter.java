package nu.marginalia.converting.processor.keywords;

import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.language.keywords.KeywordExtractor;

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
            var keywords = keywordExtractor.getProperNames(sent);
            for (var span : keywords) {
                if (span.size() <= 1)
                    continue;

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
                .flatMap(w -> instances.get(w).stream())
                .collect(Collectors.toList());
    }

}

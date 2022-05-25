package nu.marginalia.util.language.processing;

import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.DocumentSentence;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.util.language.processing.model.WordSpan;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KeywordCounter {
    private final KeywordExtractor keywordExtractor;
    private final NGramDict dict;

    public KeywordCounter(NGramDict dict, KeywordExtractor keywordExtractor) {
        this.dict = dict;
        this.keywordExtractor = keywordExtractor;
    }

    public List<WordRep> count(DocumentLanguageData dld, double cutoff) {
        HashMap<String, Double> counts = new HashMap<>(1000);
        HashMap<String, HashSet<String>> instances = new HashMap<>(1000);

        for (int i = 0; i < dld.sentences.length; i++) {
            DocumentSentence sent = dld.sentences[i];
            double value = 1.0 / Math.log(1+i);
            var keywords = keywordExtractor.getKeywordsFromSentence(sent);
            for (var span : keywords) {
                var stemmed = sent.constructStemmedWordFromSpan(span);
                if (stemmed.isBlank())
                    continue;

                counts.merge(stemmed, value, Double::sum);

                instances.computeIfAbsent(stemmed, k -> new HashSet<>()).add(sent.constructWordFromSpan(span));
            }
        }

        var topWords = counts.entrySet().stream()
                .filter(w -> w.getValue() > cutoff)
                .sorted(Comparator.comparing(this::getTermValue))
                .limit(Math.min(100, counts.size()/2))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        var topWordsSet = new HashSet<>(topWords);

        final Set<WordRep> keywords = new HashSet<>();

        for (var sentence : dld.sentences) {
            for (WordSpan kw : keywordExtractor.getKeywordsFromSentence(sentence)) {
                String stemmedWord = sentence.constructStemmedWordFromSpan(kw);
                if (topWords.contains(stemmedWord)) {
                    keywords.add(new WordRep(sentence, kw));
                }
            }
        }

        for (var sentence : dld.sentences) {
            for (var kw : keywordExtractor.getKeywordsFromSentenceStrict(sentence, topWordsSet, true)) {
                keywords.add(new WordRep(sentence, kw));
            }
        }

        Map<String, Integer> sortOrder = IntStream.range(0, topWords.size()).boxed().collect(Collectors.toMap(topWords::get, i->i));

        Comparator<WordRep> comp = Comparator.comparing(wr -> sortOrder.getOrDefault(wr.stemmed, topWords.size()));

        var ret = new ArrayList<>(keywords);
        ret.sort(comp);
        return ret;
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
        return (1+Math.log(value)) * Math.log((1.+dict.getTermFreq(key))/11820118.);
    }


}

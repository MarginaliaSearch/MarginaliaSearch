package nu.marginalia.wmsa.edge.converting.processor.logic;

import ca.rmen.porterstemmer.PorterStemmer;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDomain;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;

import java.util.*;

public class CommonKeywordExtractor {
    private final PorterStemmer ps = new PorterStemmer();

    private static final int MIN_REQUIRED_DOCUMENTS = 25;

    private static final int REQUIRED_TOTAL_COUNT_FOR_CONSIDERATION = 100;
    private static final double QUALIFYING_PROPORTION_FOR_KEYWORD = .25;

    private static final int MAX_SITE_KEYWORDS_TO_EXTRACT = 5;

    public List<String> getCommonSiteWords(ProcessedDomain ret, IndexBlock... sourceBlocks) {

        if (ret.documents.size() < MIN_REQUIRED_DOCUMENTS)
            return Collections.emptyList();

        final Map<String, String> wordToStemmedMemoized = new HashMap<>(ret.documents.size()*10);

        final Map<String, Integer> topStemmedKeywordCount = new HashMap<>(ret.documents.size()*10);
        final Map<String, Set<String>> stemmedToNonstemmedVariants = new HashMap<>(ret.documents.size()*10);

        int qualifiedDocCount = 0;
        for (var doc : ret.documents) {
            if (doc.words == null)
                continue;

            qualifiedDocCount++;

            for (var block : sourceBlocks) {
                for (var word : doc.words.get(block).words) {
                    String wordStemmed = wordToStemmedMemoized.computeIfAbsent(word, ps::stemWord);

                    // Count by negative values to sort by Map.Entry.comparingByValue() in reverse
                    topStemmedKeywordCount.merge(wordStemmed, -1, Integer::sum);

                    stemmedToNonstemmedVariants.computeIfAbsent(wordStemmed, w -> new HashSet<>()).add(word);
                }
            }
        }

        int totalValue = 0;
        for (int value : topStemmedKeywordCount.values()) {
            totalValue += value;
        }

        if (totalValue > -REQUIRED_TOTAL_COUNT_FOR_CONSIDERATION)
            return Collections.emptyList();

        List<String> topWords = new ArrayList<>(MAX_SITE_KEYWORDS_TO_EXTRACT);

        double qualifyingValue = -qualifiedDocCount * QUALIFYING_PROPORTION_FOR_KEYWORD;

        topStemmedKeywordCount.entrySet().stream()
                .filter(e -> e.getValue() < qualifyingValue)
                .sorted(Map.Entry.comparingByValue())
                .limit(MAX_SITE_KEYWORDS_TO_EXTRACT)
                .forEach(e -> topWords.addAll(stemmedToNonstemmedVariants.get(e.getKey())));


        return topWords;

    }

}

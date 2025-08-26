package nu.marginalia.keyword.extractors;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.keyword.KeywordExtractor;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.language.pos.PosPatternCategory;

import java.util.*;
import java.util.stream.Collectors;

/** Keywords that look like they could be a name */
public class NameLikeKeywords implements WordReps {
    private final List<WordRep> nameWords;
    private final Set<String> stemmed;

    public NameLikeKeywords(KeywordExtractor keywordExtractor, DocumentLanguageData dld, int minCount) {
        var counts = new Object2IntOpenHashMap<String>(100);
        var instances = new HashMap<String, HashSet<WordRep>>(100);

        for (DocumentSentence sent : dld) {
            var keywords = keywordExtractor.matchGrammarPattern(sent, PosPatternCategory.NAME);
            for (var span : keywords) {
                if (span.size() <= 1 && sent.isAllCaps(span.start))
                    continue;

                var stemmed = sent.constructStemmedWordFromSpan(span);

                counts.addTo(stemmed, -1);
                instances.computeIfAbsent(stemmed, k -> new HashSet<>()).add(new WordRep(sent, span));
            }
        }

        nameWords = counts.object2IntEntrySet().stream()
                .filter(e -> hasEnough(e, minCount))
                .sorted(Comparator.comparingInt(Object2IntMap.Entry::getIntValue))
                .limit(150)
                .map(Map.Entry::getKey)
                .flatMap(w -> instances.get(w).stream())
                .collect(Collectors.toList());

        stemmed = nameWords.stream().map(WordRep::getStemmed).collect(Collectors.toSet());
    }

    public boolean hasEnough(Object2IntMap.Entry<String> entry, int minCount) {
        final int count = -entry.getIntValue();

        if (entry.getKey().contains("_")) {
            return count >= minCount;
        }
        else {
            return count >= minCount + 1;
        }
    }

    public boolean contains(String wordStemmed) {
        return stemmed.contains(wordStemmed);
    }

    @Override
    public Collection<WordRep> getReps() {
        return nameWords;
    }
}

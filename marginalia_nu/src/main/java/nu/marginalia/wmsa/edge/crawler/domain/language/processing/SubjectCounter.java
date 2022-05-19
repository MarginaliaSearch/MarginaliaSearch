package nu.marginalia.wmsa.edge.crawler.domain.language.processing;

import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.DocumentLanguageData;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.WordRep;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.WordSpan;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.tag.WordSeparator;

import java.util.*;
import java.util.stream.Collectors;

public class SubjectCounter {
    private final KeywordExtractor keywordExtractor;

    public SubjectCounter(KeywordExtractor keywordExtractor) {
        this.keywordExtractor = keywordExtractor;
    }

    public List<WordRep> count(DocumentLanguageData dld) {

        Map<WordRep, Integer> counts = new HashMap<>();
        for (var sentence : dld.sentences) {
            for (WordSpan kw : keywordExtractor.getNames(sentence)) {
                if (kw.end + 2 >= sentence.length()) {
                    continue;
                }
                if (sentence.separators[kw.end] == WordSeparator.COMMA
                        || sentence.separators[kw.end + 1] == WordSeparator.COMMA)
                    break;

                if (("VBZ".equals(sentence.posTags[kw.end]) || "VBP".equals(sentence.posTags[kw.end]))
                        && ("DT".equals(sentence.posTags[kw.end + 1]) || "RB".equals(sentence.posTags[kw.end]) || sentence.posTags[kw.end].startsWith("VB"))
                ) {
                    counts.merge(new WordRep(sentence, new WordSpan(kw.start, kw.end)), -1, Integer::sum);
                }
            }
        }

        int best = counts.values().stream().mapToInt(Integer::valueOf).min().orElse(0);

        return counts.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .filter(e -> e.getValue()<-2 && e.getValue()<best*0.75)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

}

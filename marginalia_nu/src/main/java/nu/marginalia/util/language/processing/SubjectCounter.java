package nu.marginalia.util.language.processing;

import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.util.language.processing.model.WordSpan;
import nu.marginalia.util.language.processing.model.tag.WordSeparator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SubjectCounter {
    private final KeywordExtractor keywordExtractor;

    public SubjectCounter(KeywordExtractor keywordExtractor) {
        this.keywordExtractor = keywordExtractor;
    }

    // Seeks out subjects in a sentence by constructs like
    //
    // [Name] (Verbs) (the|a|Adverb|Verb) ...
    // e.g.
    //
    // Greeks bearing gifts -> Greeks
    // Steve McQueen drove fast | cars -> Steve McQueen

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

                String nextTag = sentence.posTags[kw.end];
                String nextNextTag = sentence.posTags[kw.end+1];

                if (isVerb(nextTag) && isDetOrAdverbOrVerb(nextNextTag)) {
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

    private boolean isDetOrAdverbOrVerb(String posTag) {
        return "DT".equals(posTag) // determinant
                || "RB".equals(posTag) // adverb
                || posTag.startsWith("VB")  // verb
                || posTag.startsWith("JJ"); // adjective
    }

    boolean isVerb(String posTag) {
        return posTag.startsWith("VB")
            && !posTag.equals("VB"); // not interested in the infinitive
    }

}

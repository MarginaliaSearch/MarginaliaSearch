package nu.marginalia.util.language.processing;

import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.util.language.processing.model.WordSpan;
import nu.marginalia.util.language.processing.model.tag.WordSeparator;

import java.util.*;
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

        Map<String, Integer> counts = new HashMap<>();
        Map<String, Set<WordRep>> instances = new HashMap<>();

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
                    var span = new WordSpan(kw.start, kw.end);
                    var rep = new WordRep(sentence, span);

                    String stemmed = rep.stemmed;

                    counts.merge(stemmed, -1, Integer::sum);
                    instances.computeIfAbsent(stemmed, s -> new HashSet<>()).add(rep);
                }
            }
        }

        int best = counts.values().stream().mapToInt(Integer::valueOf).min().orElse(0);

        return counts.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .filter(e -> e.getValue()<-2 && e.getValue()<=best*0.75)
                .flatMap(e -> instances.getOrDefault(e.getKey(), Collections.emptySet()).stream())
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

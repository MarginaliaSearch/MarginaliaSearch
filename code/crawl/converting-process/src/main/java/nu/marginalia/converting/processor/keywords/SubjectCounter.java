package nu.marginalia.converting.processor.keywords;

import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.KeywordMetadata;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.language.model.WordSpan;
import nu.marginalia.language.model.WordSeparator;
import nu.marginalia.language.keywords.KeywordExtractor;
import org.apache.commons.lang3.StringUtils;

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

    public List<WordRep> count(KeywordMetadata keywordMetadata, DocumentLanguageData dld) {

        Map<String, Integer> counts = new HashMap<>();
        Map<String, Set<WordRep>> instances = new HashMap<>();

        for (var sentence : dld.sentences) {
            for (WordSpan kw : keywordExtractor.getNouns(sentence)) {
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

                    instances.computeIfAbsent(stemmed, s -> new HashSet<>()).add(rep);
                }
            }
        }

        Map<String, Integer> scores = new HashMap<>(instances.size());
        for (String stemmed : instances.keySet()) {
            scores.put(stemmed, getTermTfIdf(keywordMetadata, stemmed));
        }

        return scores.entrySet().stream()
                .filter(e -> e.getValue() >= 150)
                .flatMap(e -> instances.getOrDefault(e.getKey(), Collections.emptySet()).stream())
                .collect(Collectors.toList());
    }

    private int getTermTfIdf(KeywordMetadata keywordMetadata, String stemmed) {
        if (stemmed.contains("_")) {
            int sum = 0;
            String[] parts = StringUtils.split(stemmed, '_');

            if (parts.length == 0) {
                return  0;
            }

            for (String part : parts) {
                sum += getTermTfIdf(keywordMetadata, part);
            }

            return sum / parts.length;
        }

        return keywordMetadata.wordsTfIdf().getOrDefault(stemmed, 0);
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

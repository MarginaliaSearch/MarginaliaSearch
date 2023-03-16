package nu.marginalia.keyword.extractors;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nu.marginalia.keyword.KeywordExtractor;
import nu.marginalia.keyword.WordReps;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.language.model.WordSpan;
import nu.marginalia.language.model.WordSeparator;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class SubjectLikeKeywords implements WordReps {
    private final List<WordRep> wordList;
    private final Set<String> stemmed;

    // Seeks out subjects in a sentence by constructs like
    //
    // [Name] (Verbs) (the|a|Adverb|Verb|Noun) ...
    // e.g.
    //
    // Greeks bearing gifts -> Greeks
    // Steve McQueen drove fast | cars -> Steve McQueen

    public SubjectLikeKeywords(KeywordExtractor keywordExtractor,
                               WordsTfIdfCounts tfIdfCounts,
                               DocumentLanguageData dld) {

        Map<String, Set<WordRep>> instances = new HashMap<>();

        for (var sentence : dld.sentences) {
            for (WordSpan kw : keywordExtractor.getNouns(sentence)) {

                if (kw.end + 2 >= sentence.length()) {
                    continue;
                }
                if (sentence.separators[kw.end] == WordSeparator.COMMA
                        || sentence.separators[kw.end + 1] == WordSeparator.COMMA)
                    continue;

                String nextTag = sentence.posTags[kw.end];
                String nextNextTag = sentence.posTags[kw.end+1];

                if (isVerb(nextTag) && isDetOrAdverbOrVerbOrNoun(nextNextTag)) {
                    var span = new WordSpan(kw.start, kw.end);
                    var rep = new WordRep(sentence, span);

                    String stemmed = rep.stemmed;

                    instances.computeIfAbsent(stemmed, s -> new HashSet<>()).add(rep);
                }
            }
        }

        Object2IntOpenHashMap<String> scores = new Object2IntOpenHashMap<>(instances.size());
        for (String stemmed : instances.keySet()) {
            scores.put(stemmed, getTermTfIdf(tfIdfCounts, stemmed));
        }

        wordList = scores.object2IntEntrySet().stream()
                .filter(e -> e.getIntValue() >= 100)
                .flatMap(e -> instances.getOrDefault(e.getKey(), Collections.emptySet()).stream())
                .collect(Collectors.toList());


        stemmed = wordList.stream().map(WordRep::getStemmed).collect(Collectors.toSet());
    }

    public boolean contains(String wordStemmed) {
        return stemmed.contains(wordStemmed);
    }

    @Override
    public Collection<WordRep> getReps() {
        return wordList;
    }

    private int getTermTfIdf(WordsTfIdfCounts tfIdfCounts, String stemmed) {
        if (stemmed.contains("_")) {
            int sum = 0;
            String[] parts = StringUtils.split(stemmed, '_');

            if (parts.length == 0) {
                return  0;
            }

            for (String part : parts) {
                sum += getTermTfIdf(tfIdfCounts, part);
            }

            return sum / parts.length;
        }

        return tfIdfCounts.getTfIdf(stemmed);
    }

    private boolean isDetOrAdverbOrVerbOrNoun(String posTag) {
        return "DT".equals(posTag) // determinant
                || posTag.startsWith("RB") // adverb
                || posTag.startsWith("VB")  // verb
                || posTag.startsWith("JJ") // adjective
                || posTag.startsWith("P")
                || posTag.startsWith("NN");
    }

    boolean isVerb(String posTag) {
        return posTag.startsWith("VB")
            && !posTag.equals("VB"); // not interested in the infinitive
    }
}

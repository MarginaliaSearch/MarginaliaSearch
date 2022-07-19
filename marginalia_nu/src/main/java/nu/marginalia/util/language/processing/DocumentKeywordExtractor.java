package nu.marginalia.util.language.processing;

import com.google.common.collect.Sets;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWords;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentKeywordExtractor {

    private final KeywordExtractor keywordExtractor;
    private final KeywordCounter tfIdfCounter;
    private final NameCounter nameCounter;
    private final LongNameCounter longNameCounter;
    private final SubjectCounter subjectCounter;

    private final NGramDict dict;

    @Inject
    public DocumentKeywordExtractor(NGramDict dict) {
        this.dict = dict;

        keywordExtractor = new KeywordExtractor();

        tfIdfCounter = new KeywordCounter(dict, keywordExtractor);
        nameCounter = new NameCounter(keywordExtractor);
        longNameCounter = new LongNameCounter(dict, keywordExtractor);
        subjectCounter = new SubjectCounter(keywordExtractor);
    }

    public EdgePageWordSet extractKeywords(DocumentLanguageData documentLanguageData) {

        List<WordRep> titleWords = extractTitleWords(documentLanguageData);

        List<WordRep> wordsTfIdf = tfIdfCounter.count(documentLanguageData);
        List<WordRep> wordsNamesRepeated = nameCounter.count(documentLanguageData, 2);
        List<WordRep> wordsNamesAll = nameCounter.count(documentLanguageData, 1);
        List<WordRep> subjects = subjectCounter.count(documentLanguageData);
        List<WordRep> wordsLongName = longNameCounter.count(documentLanguageData);

        int totalSize = wordsTfIdf.size();

        List<WordRep> lowKeywords = new ArrayList<>(totalSize / 2);
        List<WordRep> midKeywords = new ArrayList<>(totalSize / 2);
        List<WordRep> topKeywords = new ArrayList<>(totalSize / 2);

        for(var v : wordsTfIdf) {
            if (topKeywords.size() <= totalSize / 10) topKeywords.add(v);
            else if (midKeywords.size() <= totalSize / 5) midKeywords.add(v);
            else lowKeywords.add(v);
        }

        var wordsToMatchWithTitle = joinWordLists(topKeywords, midKeywords, wordsNamesRepeated, subjects);

        var words = getSimpleWords(documentLanguageData);

        for (var w : wordsLongName)
            words.add(w.word);
        for (var w : lowKeywords)
            words.remove(w.word);
        for (var w : midKeywords)
            words.remove(w.word);
        for (var w : topKeywords)
            words.remove(w.word);

        var wordSet = new EdgePageWordSet(
                createWords(IndexBlock.TitleKeywords, overlappingStems(titleWords, wordsToMatchWithTitle)),
                createWords(IndexBlock.Topic, subjects),
                createWords(IndexBlock.Title, titleWords),
                createWords(IndexBlock.NamesWords, wordsNamesAll),
                createWords(IndexBlock.Top, topKeywords),
                createWords(IndexBlock.Middle, midKeywords),
                createWords(IndexBlock.Low, lowKeywords)
        );

        wordSet.append(IndexBlock.Words, words);

        return wordSet;
    }

    private List<WordRep> extractTitleWords(DocumentLanguageData documentLanguageData) {
        return Arrays.stream(documentLanguageData.titleSentences).flatMap(sent ->
                keywordExtractor.getWordsFromSentence(sent).stream().sorted().distinct().map(w -> new WordRep(sent, w)))
                .limit(100)
                .collect(Collectors.toList());
    }

    private Collection<WordRep> joinWordLists(List<WordRep>... words) {
        int size = 0;
        for (var lst : words) {
            size += lst.size();
        }
        if (size == 0)
            return Collections.emptyList();

        final LinkedHashSet<WordRep> ret = new LinkedHashSet<>(size);
        for (var lst : words) {
            ret.addAll(lst);
        }
        return ret;
    }

    @NotNull
    private Set<String> getSimpleWords(DocumentLanguageData documentLanguageData) {
        Map<String, Integer> counts = new HashMap<>(documentLanguageData.totalNumWords());

        for (var sent : documentLanguageData.sentences) {
            for (int i = 0; i < sent.length(); i++) {
                if (!sent.isStopWord(i)) {
                    String w = AsciiFlattener.flattenUnicode(sent.wordsLowerCase[i]);
                    if (counts.containsKey(w) || (WordPatterns.wordQualitiesPredicate.test(w) && WordPatterns.filter(w))) {
                        counts.merge(w, 1, Integer::sum);
                    }
                }
            }
        }

        return counts.entrySet().stream()
                .sorted(Comparator.comparing(e -> {
                    double N = 11820118.; // Number of documents in term freq dictionary

                    // Caveat: This is actually the *negated* term score, because the second logarithm has
                    // its parameter inverted (log(a^b) = b log(a); here b = -1)
                    return (1+Math.log(e.getValue())) * Math.log((1.+dict.getTermFreq(e.getKey()))/N);
                }))
                .map(Map.Entry::getKey)
                .limit(512).collect(Collectors.toCollection(LinkedHashSet::new));
    }


    public EdgePageWords createWords(IndexBlock block, Collection<WordRep> words) {
        return new EdgePageWords(block, words.stream().map(w -> w.word).map(AsciiFlattener::flattenUnicode).filter(WordPatterns.wordQualitiesPredicate).collect(Collectors.toSet()));
    }

    private Set<WordRep> overlappingStems(Collection<WordRep> wordsA, Collection<WordRep> wordsB) {
        Set<String> stemmedA = wordsA.stream().map(WordRep::getStemmed).collect(Collectors.toSet());
        Set<String> stemmedB = wordsB.stream().map(WordRep::getStemmed).collect(Collectors.toSet());
        Set<String> stemmedIntersect = Sets.intersection(stemmedA, stemmedB);
        return Stream.concat(wordsA.stream(), wordsB.stream()).filter(w -> stemmedIntersect.contains(w.getStemmed())).collect(Collectors.toSet());
    }
}

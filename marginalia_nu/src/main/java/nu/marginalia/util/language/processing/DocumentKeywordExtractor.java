package nu.marginalia.util.language.processing;

import com.google.common.collect.Sets;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWords;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentKeywordExtractor {

    private final KeywordExtractor keywordExtractor;
    private final KeywordCounter tfIdfCounter;
    private final NameCounter nameCounter;
    private final SubjectCounter subjectCounter;

    private final TermFrequencyDict dict;
    private final double docCount;

    @Inject
    public DocumentKeywordExtractor(TermFrequencyDict dict) {
        this.dict = dict;
        docCount = dict.docCount();

        keywordExtractor = new KeywordExtractor();

        tfIdfCounter = new KeywordCounter(dict, keywordExtractor);
        nameCounter = new NameCounter(keywordExtractor);
        subjectCounter = new SubjectCounter(keywordExtractor);
    }


    public EdgePageWordSet extractKeywordsMinimal(DocumentLanguageData documentLanguageData) {

        List<WordRep> titleWords = extractTitleWords(documentLanguageData);

        KeywordCounter.WordHistogram wordsTfIdf = tfIdfCounter.countHisto(documentLanguageData);
        List<WordRep> wordsNamesAll = nameCounter.count(documentLanguageData, 1);
        List<WordRep> subjects = subjectCounter.count(documentLanguageData);

        List<WordRep> midKeywords = new ArrayList<>(wordsTfIdf.mid());
        List<WordRep> topKeywords = new ArrayList<>(wordsTfIdf.top());

        Collection<String> artifacts = getArtifacts(documentLanguageData);

        return new EdgePageWordSet(
                createWords(IndexBlock.Subjects, subjects),
                createWords(IndexBlock.Title, titleWords),
                createWords(IndexBlock.NamesWords, wordsNamesAll),
                createWords(IndexBlock.Tfidf_Top, topKeywords),
                createWords(IndexBlock.Tfidf_Middle, midKeywords),
                new EdgePageWords(IndexBlock.Artifacts, artifacts)
        );
    }



    public EdgePageWordSet extractKeywords(DocumentLanguageData documentLanguageData) {

        List<WordRep> titleWords = extractTitleWords(documentLanguageData);

        KeywordCounter.WordHistogram wordsTfIdf = tfIdfCounter.countHisto(documentLanguageData);
        List<WordRep> wordsNamesAll = nameCounter.count(documentLanguageData, 1);
        List<WordRep> subjects = subjectCounter.count(documentLanguageData);

        List<WordRep> lowKeywords = new ArrayList<>(wordsTfIdf.lower());
        List<WordRep> midKeywords = new ArrayList<>(wordsTfIdf.mid());
        List<WordRep> topKeywords = new ArrayList<>(wordsTfIdf.top());

        Collection<String> artifacts = getArtifacts(documentLanguageData);

        var wordSet = new EdgePageWordSet(
                createWords(IndexBlock.Subjects, subjects),
                createWords(IndexBlock.Title, titleWords),
                createWords(IndexBlock.NamesWords, wordsNamesAll),
                createWords(IndexBlock.Tfidf_Top, topKeywords),
                createWords(IndexBlock.Tfidf_Middle, midKeywords),
                createWords(IndexBlock.Tfidf_Lower, lowKeywords),
                new EdgePageWords(IndexBlock.Artifacts, artifacts)
        );

        getSimpleWords(wordSet, documentLanguageData,
                IndexBlock.Words_1, IndexBlock.Words_2, IndexBlock.Words_4, IndexBlock.Words_8, IndexBlock.Words_16Plus);

        return wordSet;
    }

    private void getSimpleWords(EdgePageWordSet wordSet, DocumentLanguageData documentLanguageData, IndexBlock...  blocks) {

        int start = 0;
        int lengthGoal = 32;

        for (int blockIdx = 0; blockIdx < blocks.length-1 && start < documentLanguageData.sentences.length; blockIdx++) {
            IndexBlock block = blocks[blockIdx];
            Set<String> words = new HashSet<>(lengthGoal+100);

            int pos;
            int length = 0;
            for (pos = start; pos < documentLanguageData.sentences.length && length < lengthGoal; pos++) {
                var sent = documentLanguageData.sentences[pos];
                length += sent.length();

                for (var word : sent) {
                    if (!word.isStopWord()) {
                        String w = AsciiFlattener.flattenUnicode(word.wordLowerCase());
                        if (WordPatterns.singleWordQualitiesPredicate.test(w)) {
                            words.add(w);
                        }
                    }
                }
            }
            wordSet.append(block, words);
            start = pos;
            lengthGoal+=32;
        }

        if (start < documentLanguageData.sentences.length) {

            Map<String, Integer> counts = new HashMap<>(documentLanguageData.totalNumWords());
            for (int pos = start; pos < documentLanguageData.sentences.length && counts.size() < lengthGoal; pos++) {
                var sent = documentLanguageData.sentences[pos];
                for (var word : sent) {
                    if (!word.isStopWord()) {
                        String w = AsciiFlattener.flattenUnicode(word.wordLowerCase());
                        if (counts.containsKey(w) || (WordPatterns.singleWordQualitiesPredicate.test(w))) {
                            counts.merge(w, 1, Integer::sum);
                        }
                    }
                }
            }

            Set<String> lastSet;
            if (counts.size() < 1024) {
                lastSet = counts.keySet();
            }
            else {
                lastSet = counts.entrySet().stream()
                        .sorted(Comparator.comparing(e -> {
                            double N = docCount; // Number of documents in term freq dictionary

                            // Caveat: This is actually the *negated* term score, because the second logarithm has
                            // its parameter inverted (log(a^b) = b log(a); here b = -1)
                            return (1 + Math.log(e.getValue())) * Math.log((1. + dict.getTermFreq(e.getKey())) / N);
                        }))
                        .map(Map.Entry::getKey)
                        .limit(1024)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }

            wordSet.append(blocks[blocks.length - 1], lastSet);
        }
    }

    private Collection<String> getArtifacts(DocumentLanguageData documentLanguageData) {
        Set<String> reps = new HashSet<>();

        for (var sent : documentLanguageData.sentences) {
            for (var word : sent) {
                String lc = word.wordLowerCase();
                if (lc.matches("[a-zA-Z0-9._\\-]+@[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)+")) {
                    reps.add(lc);

                    String domain = lc.substring(lc.indexOf('@'));
                    String user = lc.substring(0, lc.indexOf('@'));

                    if (!domain.equals("@hotmail.com") && !domain.equals("@gmail.com")  && !domain.equals("@paypal.com")) {
                        reps.add(domain);
                    }
                    if (!user.equals("info") && !user.equals("legal") && !user.equals("contact") && !user.equals("donotreply")) {
                        reps.add(user);
                    }

                }
            }
        }
        return reps;
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

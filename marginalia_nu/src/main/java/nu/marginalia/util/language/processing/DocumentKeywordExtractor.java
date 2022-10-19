package nu.marginalia.util.language.processing;

import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.KeywordMetadata;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordFlags;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWords;

import javax.inject.Inject;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DocumentKeywordExtractor {

    private final KeywordExtractor keywordExtractor;
    private final KeywordCounter tfIdfCounter;
    private final NameCounter nameCounter;
    private final SubjectCounter subjectCounter;


    @Inject
    public DocumentKeywordExtractor(TermFrequencyDict dict) {
        keywordExtractor = new KeywordExtractor();

        tfIdfCounter = new KeywordCounter(dict, keywordExtractor);
        nameCounter = new NameCounter(keywordExtractor);
        subjectCounter = new SubjectCounter(keywordExtractor);
    }


    public EdgePageWordSet extractKeywordsMinimal(DocumentLanguageData documentLanguageData, KeywordMetadata keywordMetadata) {

        List<WordRep> titleWords = extractTitleWords(documentLanguageData);
        List<WordRep> wordsNamesAll = nameCounter.count(documentLanguageData, 2);
        List<WordRep> subjects = subjectCounter.count(documentLanguageData);

        tfIdfCounter.countHisto(keywordMetadata, documentLanguageData);

        for (var rep : titleWords) keywordMetadata.titleKeywords().add(rep.stemmed);
        for (var rep : wordsNamesAll) keywordMetadata.namesKeywords().add(rep.stemmed);
        for (var rep : subjects) keywordMetadata.subjectKeywords().add(rep.stemmed);

        List<String> artifacts = getArtifacts(documentLanguageData);

        keywordMetadata.flagsTemplate().add(EdgePageWordFlags.Simple);

        return new EdgePageWordSet(
                createWords(keywordMetadata, IndexBlock.Title, titleWords),
                EdgePageWords.withBlankMetadata(IndexBlock.Artifacts, artifacts)
        );
    }

    public EdgePageWordSet extractKeywords(DocumentLanguageData documentLanguageData, KeywordMetadata keywordMetadata) {

        List<WordRep> titleWords = extractTitleWords(documentLanguageData);

        getWordPositions(keywordMetadata, documentLanguageData);

        List<WordRep> wordsNamesAll = nameCounter.count(documentLanguageData, 2);
        List<WordRep> subjects = subjectCounter.count(documentLanguageData);

        List<WordRep> wordsTfIdf = tfIdfCounter.countHisto(keywordMetadata, documentLanguageData);

        for (var rep : titleWords) keywordMetadata.titleKeywords().add(rep.stemmed);
        for (var rep : wordsNamesAll) keywordMetadata.namesKeywords().add(rep.stemmed);
        for (var rep : subjects) keywordMetadata.subjectKeywords().add(rep.stemmed);

        List<String> artifacts = getArtifacts(documentLanguageData);

        var wordSet = new EdgePageWordSet(
                createWords(keywordMetadata, IndexBlock.Title, titleWords),
                createWords(keywordMetadata, IndexBlock.Tfidf_High, wordsTfIdf),
                createWords(keywordMetadata, IndexBlock.Subjects, subjects),
                EdgePageWords.withBlankMetadata(IndexBlock.Artifacts, artifacts)
        );

        getSimpleWords(keywordMetadata, wordSet, documentLanguageData,
                IndexBlock.Words_1, IndexBlock.Words_2, IndexBlock.Words_4, IndexBlock.Words_8, IndexBlock.Words_16Plus);

        return wordSet;
    }


    public void getWordPositions(KeywordMetadata keywordMetadata, DocumentLanguageData dld) {
        Map<String, Integer> ret = keywordMetadata.positionMask();

        int posCtr = 0;
        for (var sent : dld.titleSentences) {
            int posBit = (int)((1L << (posCtr/4)) & 0xFFFF_FFFFL);

            for (var word : sent) {
                ret.merge(word.stemmed(), posBit, this::bitwiseOr);
            }

            for (var span : keywordExtractor.getNames(sent)) {
                ret.merge(sent.constructStemmedWordFromSpan(span), posBit, this::bitwiseOr);
            }
        }
        posCtr+=4;
        for (var sent : dld.sentences) {
            int posBit = (int)((1L << (posCtr/4)) & 0xFFFF_FFFFL);

            for (var word : sent) {
                ret.merge(word.stemmed(), posBit, this::bitwiseOr);
            }

            for (var span : keywordExtractor.getNames(sent)) {
                ret.merge(sent.constructStemmedWordFromSpan(span), posBit, this::bitwiseOr);
            }

            posCtr++;
        }
    }

    private int bitwiseOr(int a, int b) {
        return a | b;
    }


    private void getSimpleWords(KeywordMetadata metadata, EdgePageWordSet wordSet, DocumentLanguageData documentLanguageData, IndexBlock...  blocks) {

        EnumSet<EdgePageWordFlags> flagsTemplate = EnumSet.noneOf(EdgePageWordFlags.class);

        int start = 0;
        int lengthGoal = 32;

        for (int blockIdx = 0; blockIdx < blocks.length && start < documentLanguageData.sentences.length; blockIdx++) {
            IndexBlock block = blocks[blockIdx];
            Set<EdgePageWords.Entry> words = new HashSet<>(lengthGoal+100);

            int pos;
            int length = 0;
            for (pos = start; pos < documentLanguageData.sentences.length && length < lengthGoal; pos++) {
                var sent = documentLanguageData.sentences[pos];
                length += sent.length();

                for (var word : sent) {
                    if (!word.isStopWord()) {
                        String w = AsciiFlattener.flattenUnicode(word.wordLowerCase());
                        if (WordPatterns.singleWordQualitiesPredicate.test(w)) {
                            words.add(new EdgePageWords.Entry(w, metadata.forWord(flagsTemplate, word.stemmed())));
                        }
                    }
                }

                for (var names : keywordExtractor.getNames(sent)) {
                    var rep = new WordRep(sent, names);
                    String w = AsciiFlattener.flattenUnicode(rep.word);

                    words.add(new EdgePageWords.Entry(w, metadata.forWord(flagsTemplate, rep.stemmed)));
                }
            }
            wordSet.append(block, words);
            start = pos;
            lengthGoal+=32;
        }
    }

    private static final Pattern mailLikePattern = Pattern.compile("[a-zA-Z0-9._\\-]+@[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)+");
    private List<String> getArtifacts(DocumentLanguageData documentLanguageData) {
        Set<String> reps = new HashSet<>();

        for (var sent : documentLanguageData.sentences) {
            for (var word : sent) {
                String lc = word.wordLowerCase();
                if (lc.length() > 6
                    && lc.indexOf('@') > 0
                    && mailLikePattern.matcher(lc).matches()) {

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
        return new ArrayList<>(reps);
    }

    private List<WordRep> extractTitleWords(DocumentLanguageData documentLanguageData) {
        return Arrays.stream(documentLanguageData.titleSentences).flatMap(sent ->
                keywordExtractor.getWordsFromSentence(sent).stream().sorted().distinct().map(w -> new WordRep(sent, w)))
                .limit(100)
                .collect(Collectors.toList());
    }

    public EdgePageWords createWords(KeywordMetadata metadata,
                                     IndexBlock block,
                                     Collection<WordRep> words) {

        Set<EdgePageWords.Entry> entries = new HashSet<>(words.size());
        for (var word : words) {

            String flatWord = AsciiFlattener.flattenUnicode(word.word);
            if (!WordPatterns.hasWordQualities(flatWord)) {
                continue;
            }

            entries.add(new EdgePageWords.Entry(flatWord, metadata.forWord(metadata.flagsTemplate(), word.stemmed)));
        }

        return new EdgePageWords(block, entries);
    }
}

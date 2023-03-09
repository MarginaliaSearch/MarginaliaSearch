package nu.marginalia.converting.processor.keywords;

import nu.marginalia.converting.processor.keywords.extractors.*;
import nu.marginalia.language.WordPatterns;
import nu.marginalia.language.encoding.AsciiFlattener;
import nu.marginalia.language.keywords.KeywordExtractor;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.KeywordMetadata;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.converting.model.DocumentKeywordsBuilder;
import nu.marginalia.language.statistics.TermFrequencyDict;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class DocumentKeywordExtractor {

    private final KeywordExtractor keywordExtractor;
    private final KeywordCounter tfIdfCounter;
    private final NameCounter nameCounter;
    private final SubjectCounter subjectCounter;
    private final ArtifactKeywords artifactKeywords;

    private final SimpleKeywords simpleKeywords;
    private final DocumentKeywordPositionBitmaskExtractor keywordPositions;


    @Inject
    public DocumentKeywordExtractor(TermFrequencyDict dict) {
        keywordExtractor = new KeywordExtractor();

        keywordPositions = new DocumentKeywordPositionBitmaskExtractor(keywordExtractor);
        artifactKeywords = new ArtifactKeywords();

        tfIdfCounter = new KeywordCounter(dict, keywordExtractor);
        nameCounter = new NameCounter(keywordExtractor);
        subjectCounter = new SubjectCounter(keywordExtractor);
        simpleKeywords = new SimpleKeywords(keywordExtractor);
    }


    public DocumentKeywordsBuilder extractKeywords(DocumentLanguageData documentLanguageData) {

        KeywordMetadata keywordMetadata = keywordPositions.getWordPositions(documentLanguageData);

        List<WordRep> wordsTfIdf = tfIdfCounter.updateWordStatistics(keywordMetadata, documentLanguageData);

        List<WordRep> titleWords = extractTitleWords(documentLanguageData);
        List<WordRep> wordsNamesAll = nameCounter.count(documentLanguageData, 2);
        List<WordRep> subjects = subjectCounter.count(keywordMetadata, documentLanguageData);

        List<String> artifacts = artifactKeywords.getArtifactKeywords(documentLanguageData);

        for (var rep : titleWords) keywordMetadata.titleKeywords().add(rep.stemmed);
        for (var rep : wordsNamesAll) keywordMetadata.namesKeywords().add(rep.stemmed);
        for (var rep : subjects) keywordMetadata.subjectKeywords().add(rep.stemmed);

        DocumentKeywordsBuilder wordsBuilder = new DocumentKeywordsBuilder();

        simpleKeywords.getSimpleWords(wordsBuilder, keywordMetadata, documentLanguageData);

        createWords(wordsBuilder, keywordMetadata, wordsTfIdf);
        createWords(wordsBuilder, keywordMetadata, titleWords);
        createWords(wordsBuilder, keywordMetadata, subjects);

        wordsBuilder.addAllSyntheticTerms(artifacts);

        return wordsBuilder;
    }


    private List<WordRep> extractTitleWords(DocumentLanguageData documentLanguageData) {
        return Arrays.stream(documentLanguageData.titleSentences).flatMap(sent ->
                keywordExtractor.getWordsFromSentence(sent).stream().sorted().distinct().map(w -> new WordRep(sent, w)))
                .limit(100)
                .collect(Collectors.toList());
    }

    public void createWords(DocumentKeywordsBuilder wordsBuilder,
                            KeywordMetadata metadata,
                            Collection<WordRep> words) {

        for (var word : words) {

            String flatWord = AsciiFlattener.flattenUnicode(word.word);

            if (WordPatterns.hasWordQualities(flatWord)) {
                wordsBuilder.add(flatWord, metadata.getMetadataForWord(metadata.wordFlagsTemplate(), word.stemmed));
            }
        }
    }

}

package nu.marginalia.keyword_extraction;

import nu.marginalia.keyword_extraction.extractors.*;
import nu.marginalia.keyword_extraction.model.DocumentKeywordsBuilder;
import nu.marginalia.language.WordPatterns;
import nu.marginalia.language.encoding.AsciiFlattener;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.language.statistics.TermFrequencyDict;
import nu.marginalia.model.EdgeUrl;

import javax.inject.Inject;

public class DocumentKeywordExtractor {

    private final KeywordExtractor keywordExtractor;
    private final TermFrequencyDict dict;


    @Inject
    public DocumentKeywordExtractor(TermFrequencyDict dict) {
        this.dict = dict;
        this.keywordExtractor = new KeywordExtractor();
    }


    public DocumentKeywordsBuilder extractKeywords(DocumentLanguageData dld, EdgeUrl url) {

        var bitmask = new KeywordPositionBitmask(keywordExtractor, dld);
        var tfIdfCounts = new WordsTfIdfCounts(dict, keywordExtractor, dld);

        var titleKeywords = new TitleKeywords(keywordExtractor, dld);
        var nameLikeKeywords = new NameLikeKeywords(keywordExtractor, dld, 2);
        var subjectLikeKeywords = new SubjectLikeKeywords(keywordExtractor, tfIdfCounts, dld);
        var artifactKeywords = new ArtifactKeywords(dld);
        var urlKeywords = new UrlKeywords(url);

        var keywordMetadata = KeywordMetadata.builder()
                .bitmask(bitmask)
                .tfIdfCounts(tfIdfCounts)
                .titleKeywords(titleKeywords)
                .nameLikeKeywords(nameLikeKeywords)
                .subjectLikeKeywords(subjectLikeKeywords)
                .urlKeywords(urlKeywords)
                .build();

        DocumentKeywordsBuilder wordsBuilder = new DocumentKeywordsBuilder();

        createSimpleWords(wordsBuilder, keywordMetadata, dld);

        createWordsFromSet(wordsBuilder, keywordMetadata, tfIdfCounts);
        createWordsFromSet(wordsBuilder, keywordMetadata, titleKeywords);
        createWordsFromSet(wordsBuilder, keywordMetadata, subjectLikeKeywords);
        createWordsFromSet(wordsBuilder, keywordMetadata, nameLikeKeywords);

        wordsBuilder.addAllSyntheticTerms(artifactKeywords.getWords());

        return wordsBuilder;
    }


    private void createWordsFromSet(DocumentKeywordsBuilder wordsBuilder,
                                   KeywordMetadata metadata,
                                   WordReps words) {

        for (var word : words.getReps()) {

            String flatWord = AsciiFlattener.flattenUnicode(word.word);

            if (WordPatterns.hasWordQualities(flatWord)) {
                wordsBuilder.add(flatWord, metadata.getMetadataForWord(word.stemmed));
            }
        }
    }

    private void createSimpleWords(DocumentKeywordsBuilder wordsBuilder,
                                  KeywordMetadata metadata,
                                  DocumentLanguageData documentLanguageData)
    {
        for (var sent : documentLanguageData.sentences) {

            if (wordsBuilder.size() > 1500)
                break;

            for (var word : sent) {
                if (word.isStopWord()) {
                    continue;
                }

                String w = AsciiFlattener.flattenUnicode(word.wordLowerCase());
                if (WordPatterns.singleWordQualitiesPredicate.test(w)) {
                    wordsBuilder.add(w, metadata.getMetadataForWord(word.stemmed()));
                }
            }

            for (var names : keywordExtractor.getProperNames(sent)) {
                var rep = new WordRep(sent, names);
                String w = AsciiFlattener.flattenUnicode(rep.word);

                wordsBuilder.add(w, metadata.getMetadataForWord(rep.stemmed));
            }
        }
    }
}

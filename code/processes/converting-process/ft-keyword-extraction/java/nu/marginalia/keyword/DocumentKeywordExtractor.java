package nu.marginalia.keyword;

import com.google.inject.Inject;
import nu.marginalia.WmsaHome;
import nu.marginalia.keyword.extractors.*;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;

import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;

public class DocumentKeywordExtractor {

    private final TermFrequencyDict dict;

    private final KeywordExtractor keywordExtractor = new KeywordExtractor();

    @Inject
    public DocumentKeywordExtractor(TermFrequencyDict dict) {
        this.dict = dict;
    }

    // for tests
    public DocumentKeywordExtractor() {
        try {
            this.dict = new TermFrequencyDict(WmsaHome.getLanguageModels());
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    public DocumentKeywordsBuilder extractKeywords(DocumentLanguageData dld, String language, LinkTexts linkTexts, EdgeUrl url) {

        var tfIdfCounts = new WordsTfIdfCounts(dict, keywordExtractor, dld);

        var titleKeywords = new TitleKeywords(keywordExtractor, dld);
        var nameLikeKeywords = new NameLikeKeywords(keywordExtractor, dld, 2);
        var subjectLikeKeywords = new SubjectLikeKeywords(keywordExtractor, language, tfIdfCounts, dld);
        var artifactKeywords = new ArtifactKeywords(dld);
        var urlKeywords = new UrlKeywords(url);
        var positionMapper = new DocumentPositionMapper(language);
        var keywordMetadata = KeywordMetadata.builder()
                .titleKeywords(titleKeywords)
                .nameLikeKeywords(nameLikeKeywords)
                .subjectLikeKeywords(subjectLikeKeywords)
                .urlKeywords(urlKeywords)
                .build();

        DocumentKeywordsBuilder wordsBuilder = new DocumentKeywordsBuilder();

        positionMapper.mapPositionsAndExtractSimpleKeywords(wordsBuilder, keywordMetadata, dld, linkTexts);

        createNGramTermsFromSet(wordsBuilder, keywordMetadata, titleKeywords);
        createNGramTermsFromSet(wordsBuilder, keywordMetadata, subjectLikeKeywords);
        createNGramTermsFromSet(wordsBuilder, keywordMetadata, nameLikeKeywords);

        var importantWords = getImportantWords(tfIdfCounts, nameLikeKeywords, subjectLikeKeywords, wordsBuilder);

        wordsBuilder.addImportantWords(importantWords);
        wordsBuilder.addAllSyntheticTerms(artifactKeywords.getWords());

        return wordsBuilder;
    }

    private static Collection<String> getImportantWords(WordsTfIdfCounts tfIdfCounts, NameLikeKeywords nameLikeKeywords, SubjectLikeKeywords subjectLikeKeywords, DocumentKeywordsBuilder wordsBuilder) {
        return Stream.of(nameLikeKeywords, subjectLikeKeywords)
                .flatMap(k -> k.getReps().stream())
                .filter(w -> {
                    if (w.word.length() < 3)
                        return false;
                    if (w.word.contains("_"))
                        return false;
                    return true;
                })
                .sorted(tfIdfCounts.reversed())
                .limit(16)
                .filter(w -> tfIdfCounts.termFrequencyDictValue(w) > 100)
                .sorted(Comparator.comparing(tfIdfCounts::termFrequencyDictValue))
                .limit(6)
                .map(w -> w.word)
                .toList();
    }

    private void createNGramTermsFromSet(DocumentKeywordsBuilder wordsBuilder,
                                         KeywordMetadata metadata,
                                         WordReps words) {
        for (var rep : words.getReps()) {
            var word = rep.word;

            if (!word.isBlank()) {
                byte meta = metadata.getMetadataForWord(rep.stemmed);
                wordsBuilder.addMeta(word, meta);
            }
        }
    }

}

package nu.marginalia.keyword;

import com.google.inject.Inject;
import nu.marginalia.WmsaHome;
import nu.marginalia.keyword.extractors.*;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;

import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;


public class DocumentKeywordExtractor {

    private final KeywordExtractor keywordExtractor;
    private final TermFrequencyDict dict;


    @Inject
    public DocumentKeywordExtractor(TermFrequencyDict dict) {
        this.dict = dict;
        this.keywordExtractor = new KeywordExtractor();
    }

    // for tests
    public DocumentKeywordExtractor() {
        this.dict = new TermFrequencyDict(WmsaHome.getLanguageModels());
        this.keywordExtractor = new KeywordExtractor();
    }


    public DocumentKeywordsBuilder extractKeywords(DocumentLanguageData dld, EdgeUrl url) {

        var tfIdfCounts = new WordsTfIdfCounts(dict, keywordExtractor, dld);

        var titleKeywords = new TitleKeywords(keywordExtractor, dld);
        var nameLikeKeywords = new NameLikeKeywords(keywordExtractor, dld, 2);
        var subjectLikeKeywords = new SubjectLikeKeywords(keywordExtractor, tfIdfCounts, dld);
        var artifactKeywords = new ArtifactKeywords(dld);
        var urlKeywords = new UrlKeywords(url);

        var keywordMetadata = KeywordMetadata.builder()
                .titleKeywords(titleKeywords)
                .nameLikeKeywords(nameLikeKeywords)
                .subjectLikeKeywords(subjectLikeKeywords)
                .urlKeywords(urlKeywords)
                .build();

        DocumentKeywordsBuilder wordsBuilder = new DocumentKeywordsBuilder();

        createSimpleWords(wordsBuilder, keywordMetadata, dld);

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
                long meta = metadata.getMetadataForWord(rep.stemmed);
                wordsBuilder.addMeta(word, meta);
            }
        }
    }

    private void createSimpleWords(DocumentKeywordsBuilder wordsBuilder,
                                  KeywordMetadata metadata,
                                  DocumentLanguageData documentLanguageData)
    {
        // we use 1-based indexing since the data
        // will be gamma encoded, and it can't represent 0
        int pos = 1;

        for (var sent : documentLanguageData.sentences) {

            if (wordsBuilder.size() > 1500)
                break;

            for (var word : sent) {
                if (word.isStopWord()) {
                    continue;
                }

                String w = word.wordLowerCase();
                if (matchesWordPattern(w)) {
                    /* Add information about term positions */
                    wordsBuilder.addPos(word.wordLowerCase(), pos++);

                    /* Add metadata for word */
                    wordsBuilder.addMeta(w, metadata.getMetadataForWord(word.stemmed()));
                }
            }

            for (var names : keywordExtractor.getProperNames(sent)) {
                var rep = new WordRep(sent, names);

                long meta = metadata.getMetadataForWord(rep.stemmed);

                wordsBuilder.addMeta(rep.word, meta);
            }

            for (int i = 0; i < sent.ngrams.length; i++) {
                var ngram = sent.ngrams[i];
                var ngramStemmed = sent.ngramStemmed[i];

                long meta = metadata.getMetadataForWord(ngramStemmed);

                wordsBuilder.addMeta(ngram, meta);
            }

        }
    }

    boolean matchesWordPattern(String s) {
        // this function is an unrolled version of the regexp [\da-zA-Z]{1,15}([.\-_/:+*][\da-zA-Z]{1,10}){0,4}

        String wordPartSeparator = ".-_/:+*";

        int i = 0;

        for (int run = 0; run < 15 && i < s.length(); run++, i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') continue;
            if (c >= 'A' && c <= 'Z') continue;
            if (c >= '0' && c <= '9') continue;
            break;
        }

        if (i == 0)
            return false;

        for (int j = 0; j < 5; j++) {
            if (i == s.length()) return true;

            if (wordPartSeparator.indexOf(s.charAt(i)) < 0) {
                return false;
            }

            i++;

            for (int run = 0; run < 10 && i < s.length(); run++, i++) {
                char c = s.charAt(i);
                if (c >= 'a' && c <= 'z') continue;
                if (c >= 'A' && c <= 'Z') continue;
                if (c >= '0' && c <= '9') continue;
                break;
            }
        }

        return false;
    }
}

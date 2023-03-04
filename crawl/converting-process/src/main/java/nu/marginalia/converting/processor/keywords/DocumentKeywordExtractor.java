package nu.marginalia.converting.processor.keywords;

import nu.marginalia.language.WordPatterns;
import nu.marginalia.language.encoding.AsciiFlattener;
import nu.marginalia.language.keywords.KeywordExtractor;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.KeywordMetadata;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.model.crawl.EdgePageWordFlags;
import nu.marginalia.model.crawl.EdgePageWords;
import nu.marginalia.language.statistics.TermFrequencyDict;

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


    public EdgePageWords extractKeywordsMinimal(DocumentLanguageData documentLanguageData, KeywordMetadata keywordMetadata) {

        List<WordRep> titleWords = extractTitleWords(documentLanguageData);
        List<WordRep> wordsNamesAll = nameCounter.count(documentLanguageData, 2);
        List<WordRep> subjects = subjectCounter.count(keywordMetadata, documentLanguageData);

        for (var rep : titleWords) keywordMetadata.titleKeywords().add(rep.stemmed);
        for (var rep : wordsNamesAll) keywordMetadata.namesKeywords().add(rep.stemmed);
        for (var rep : subjects) keywordMetadata.subjectKeywords().add(rep.stemmed);

        List<String> artifacts = getArtifacts(documentLanguageData);

        WordsBuilder wordsBuilder = new WordsBuilder();

        createWords(wordsBuilder, keywordMetadata, titleWords, 0);
        artifacts.forEach(wordsBuilder::addWithBlankMetadata);

        return wordsBuilder.build();
    }

    public EdgePageWords extractKeywords(DocumentLanguageData documentLanguageData, KeywordMetadata keywordMetadata) {

        List<WordRep> titleWords = extractTitleWords(documentLanguageData);

        getWordPositions(keywordMetadata, documentLanguageData);

        List<WordRep> wordsTfIdf = tfIdfCounter.countHisto(keywordMetadata, documentLanguageData);

        List<WordRep> wordsNamesAll = nameCounter.count(documentLanguageData, 2);
        List<WordRep> subjects = subjectCounter.count(keywordMetadata, documentLanguageData);


        for (var rep : titleWords) keywordMetadata.titleKeywords().add(rep.stemmed);
        for (var rep : wordsNamesAll) keywordMetadata.namesKeywords().add(rep.stemmed);
        for (var rep : subjects) keywordMetadata.subjectKeywords().add(rep.stemmed);

        List<String> artifacts = getArtifacts(documentLanguageData);

        WordsBuilder wordsBuilder = new WordsBuilder();

        createWords(wordsBuilder, keywordMetadata, titleWords, 0);
        createWords(wordsBuilder, keywordMetadata, wordsTfIdf, EdgePageWordFlags.TfIdfHigh.asBit());
        createWords(wordsBuilder, keywordMetadata, subjects, 0);

        getSimpleWords(wordsBuilder, keywordMetadata, documentLanguageData);

        artifacts.forEach(wordsBuilder::addWithBlankMetadata);

        return wordsBuilder.build();
    }


    public void getWordPositions(KeywordMetadata keywordMetadata, DocumentLanguageData dld) {
        Map<String, Integer> ret = keywordMetadata.positionMask();

        for (var sent : dld.titleSentences) {
            int posBit = 1;

            for (var word : sent) {
                ret.merge(word.stemmed(), posBit, this::bitwiseOr);
            }

            for (var span : keywordExtractor.getProperNames(sent)) {
                ret.merge(sent.constructStemmedWordFromSpan(span), posBit, this::bitwiseOr);
            }
        }

        int pos = 1;
        int line = 0;
        for (var sent : dld.sentences) {
            int posBit = (int)((1L << pos) & 0xFFFF_FFFFL);

            for (var word : sent) {
                ret.merge(word.stemmed(), posBit, this::bitwiseOr);
            }

            for (var span : keywordExtractor.getProperNames(sent)) {
                ret.merge(sent.constructStemmedWordFromSpan(span), posBit, this::bitwiseOr);
            }

            if (pos < 4) pos ++;
            else if (pos < 8) {
                if (++line >= 2) {
                    pos++;
                    line = 0;
                }
            }
            else if (pos < 24) {
                if (++line >= 4) {
                    pos++;
                    line = 0;
                }
            }
            else if (pos < 64) {
                if (++line > 8) {
                    pos++;
                    line = 0;
                }
            }
            else {
                break;
            }
        }
    }

    private int bitwiseOr(int a, int b) {
        return a | b;
    }


    private void getSimpleWords(WordsBuilder wordsBuilder, KeywordMetadata metadata, DocumentLanguageData documentLanguageData) {

        EnumSet<EdgePageWordFlags> flagsTemplate = EnumSet.noneOf(EdgePageWordFlags.class);

        for (var sent : documentLanguageData.sentences) {

            if (wordsBuilder.size() > 1500)
                break;

            for (var word : sent) {
                if (!word.isStopWord()) {
                    String w = AsciiFlattener.flattenUnicode(word.wordLowerCase());
                    if (WordPatterns.singleWordQualitiesPredicate.test(w)) {
                        wordsBuilder.add(w, metadata.getMetadataForWord(flagsTemplate, word.stemmed()));
                    }
                }
            }

            for (var names : keywordExtractor.getProperNames(sent)) {
                var rep = new WordRep(sent, names);
                String w = AsciiFlattener.flattenUnicode(rep.word);

                wordsBuilder.add(w, metadata.getMetadataForWord(flagsTemplate, rep.stemmed));
            }
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

    public void createWords(WordsBuilder wordsBuilder,
                                     KeywordMetadata metadata,
                                     Collection<WordRep> words,
                                     long additionalMeta) {

        for (var word : words) {

            String flatWord = AsciiFlattener.flattenUnicode(word.word);
            if (!WordPatterns.hasWordQualities(flatWord)) {
                continue;
            }

            wordsBuilder.add(flatWord, metadata.getMetadataForWord(metadata.wordFlagsTemplate(), word.stemmed) | additionalMeta);
        }
    }

    private static class WordsBuilder {
        private final EdgePageWords words = new EdgePageWords(1600);
        private final Set<String> seen = new HashSet<>(1600);

        public void add(String word, long meta) {
            if (seen.add(word)) {
                words.add(word, meta);
            }
        }
        public void addWithBlankMetadata(String word) {
            if (seen.add(word)) {
                words.addJustNoMeta(word);
            }
        }

        public EdgePageWords build() {
            return words;
        }

        public int size() {
            return seen.size();
        }
    }
}

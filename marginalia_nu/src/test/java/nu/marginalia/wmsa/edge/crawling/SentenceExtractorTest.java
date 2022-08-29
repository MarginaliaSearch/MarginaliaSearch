package nu.marginalia.wmsa.edge.crawling;

import lombok.SneakyThrows;
import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.util.language.processing.DocumentKeywordExtractor;
import nu.marginalia.util.language.processing.KeywordExtractor;
import nu.marginalia.util.language.processing.SentenceExtractor;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.util.language.processing.model.WordSpan;
import nu.marginalia.util.language.processing.model.tag.WordSeparator;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.integration.wikipedia.WikipediaReader;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

class SentenceExtractorTest {
    SentenceExtractor newSe;
    SentenceExtractor legacySe;
    final LanguageModels lm = TestLanguageModels.getLanguageModels();

    @BeforeEach
    public void setUp() {

        newSe = new SentenceExtractor(lm);
        legacySe = new SentenceExtractor(lm);
        legacySe.setLegacyMode(true);
    }

    @SneakyThrows
    @Test
    void testExtractSubject() {
        var data = Path.of("/home/vlofgren/Code/tmp-data/");

        System.out.println("Running");

        var dict = new TermFrequencyDict(lm);

        SentenceExtractor se = new SentenceExtractor(lm);
        KeywordExtractor keywordExtractor = new KeywordExtractor();

        for (var file : Objects.requireNonNull(data.toFile().listFiles())) {
            System.out.println(file);
            var dld = se.extractSentences(Jsoup.parse(Files.readString(file.toPath())));
            Map<String, Integer> counts = new HashMap<>();
            for (var sentence : dld.sentences) {
                for (WordSpan kw : keywordExtractor.getNames(sentence)) {
                    if (kw.end + 2 >= sentence.length()) {
                        continue;
                    }
                    if (sentence.separators[kw.end] == WordSeparator.COMMA
                            || sentence.separators[kw.end + 1] == WordSeparator.COMMA)
                        break;

                    if (("VBZ".equals(sentence.posTags[kw.end]) || "VBP".equals(sentence.posTags[kw.end]))
                            && ("DT".equals(sentence.posTags[kw.end + 1]) || "RB".equals(sentence.posTags[kw.end]) || sentence.posTags[kw.end].startsWith("VB"))
                    ) {
                        counts.merge(new WordRep(sentence, new WordSpan(kw.start, kw.end)).word, -1, Integer::sum);
                    }
                }
            }

            int best = counts.values().stream().mapToInt(Integer::valueOf).min().orElse(0);

            counts.entrySet().stream().sorted(Map.Entry.comparingByValue())
                    .filter(e -> e.getValue()<-2 && e.getValue()<best*0.75)
                    .forEach(System.out::println);
        }

    }

    @Test
    public void testWikipedia() throws InterruptedException {

        System.out.println("Running");

        var dict = new TermFrequencyDict(lm);

        DocumentKeywordExtractor documentKeywordExtractor = new DocumentKeywordExtractor(dict);

        var reader = new WikipediaReader("/home/vlofgren/Work/wikipedia_en_100_nopic_2021-06.zim", new EdgeDomain("encyclopedia.marginalia.nu"),
                post -> {

                    var newResult = newSe.extractSentences(Jsoup.parse(post.body));

                    var newRes = documentKeywordExtractor.extractKeywords(newResult);
                    System.out.println(newRes);
                });
        reader.join();
    }

    @Test
    public void testPattern() {
        System.out.println(WordPatterns.singleWordAdditionalPattern.matcher("2.6.18164.el5pae").matches());
    }
    @Test
    void extractSentences() throws IOException {
        var data = Path.of("/home/vlofgren/Code/tmp-data/");

        System.out.println("Running");

        var dict = new TermFrequencyDict(lm);

        DocumentKeywordExtractor documentKeywordExtractor = new DocumentKeywordExtractor(dict);

//        documentKeywordExtractorLegacy.setLegacy(true);

//        for (;;) {
            long st = System.currentTimeMillis();
            for (var file : Objects.requireNonNull(data.toFile().listFiles())) {


                var newResult = newSe.extractSentences(Jsoup.parse(Files.readString(file.toPath())));

                var newRes = documentKeywordExtractor.extractKeywords(newResult);


//                var legacyRes = documentKeywordExtractorLegacy.extractKeywords(newResult);
//
//                EdgePageWordSet difference = new EdgePageWordSet();
//                for (IndexBlock block : IndexBlock.values()) {

//                    var newWords = new HashSet<>(newRes.get(block).words);
//                    var oldWords = new HashSet<>(legacyRes.get(block).words);
//                    newWords.removeAll(oldWords);

//                    if (!newWords.isEmpty()) {
//                        difference.append(block, newWords);
//                    }
//                }
//                System.out.println(difference);
                System.out.println(newRes);
//                System.out.println("---");
            }
            System.out.println(System.currentTimeMillis() - st);
//        }

    }

    @SneakyThrows
    @Test
    @Disabled
    public void testSE() {
        var result = newSe.extractSentences(Jsoup.parse(new URL("https://memex.marginalia.nu/log/26-personalized-pagerank.gmi"), 10000));

        var dict = new TermFrequencyDict(lm);
        System.out.println(new DocumentKeywordExtractor(dict).extractKeywords(result));


//
//        var pke = new PositionKeywordExtractor(dict, new KeywordExtractor());
//        pke.count(result).stream().map(wr -> wr.word).distinct().forEach(System.out::println);
//        for (var sent : result.sentences) {
//            System.out.println(sent);
//        }

    }

    @Test
    public void separatorExtraction() {
        seprateExtractor("Cookies, cream and shoes");
        seprateExtractor("Cookies");
        seprateExtractor("");

    }

    final Pattern p = Pattern.compile("([, ]+)");
    public void seprateExtractor(String sentence) {
        var matcher = p.matcher(sentence);

        Arrays.stream(p.split(sentence)).forEach(System.out::println);
        List<String> words = new ArrayList<>();
        List<String> separators = new ArrayList<>();

        int start = 0;
        int wordStart = 0;
        while (wordStart <= sentence.length()) {
            if (!matcher.find(wordStart)) {
                words.add(sentence.substring(wordStart));
                separators.add("S");
                break;
            }

            if (wordStart != matcher.start()) {
                words.add(sentence.substring(wordStart, matcher.start()));
                separators.add(sentence.substring(matcher.start(), matcher.end()).isBlank() ? "S" : "C");
            }
            wordStart = matcher.end();
        }

        System.out.println(words);
        System.out.println(separators);
    }
}
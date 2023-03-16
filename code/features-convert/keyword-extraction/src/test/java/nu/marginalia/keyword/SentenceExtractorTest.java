package nu.marginalia.keyword;

import lombok.SneakyThrows;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.language.model.WordSpan;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import nu.marginalia.language.model.WordSeparator;
import nu.marginalia.WmsaHome;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.test.util.TestLanguageModels;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@Tag("slow")
class SentenceExtractorTest {
    SentenceExtractor newSe;
    SentenceExtractor legacySe;
    final LanguageModels lm = TestLanguageModels.getLanguageModels();

    @BeforeEach
    public void setUp() {

        newSe = new SentenceExtractor(lm);
        legacySe = new SentenceExtractor(lm);
    }


    @SneakyThrows
    public static void main(String... args) throws IOException {
        final LanguageModels lm = TestLanguageModels.getLanguageModels();

        var data = WmsaHome.getHomePath().resolve("test-data/");

        System.out.println("Running");

        SentenceExtractor se = new SentenceExtractor(lm);

        var dict = new TermFrequencyDict(lm);
        var url = new EdgeUrl("https://memex.marginalia.nu/");
        DocumentKeywordExtractor documentKeywordExtractor = new DocumentKeywordExtractor(dict);

        for (;;) {
            long total = 0;
            for (var file : Objects.requireNonNull(data.toFile().listFiles())) {
                var doc = Jsoup.parse(Files.readString(file.toPath()));
                long start = System.currentTimeMillis();
                var dld = se.extractSentences(doc);
                documentKeywordExtractor.extractKeywords(dld, url);
                total += (System.currentTimeMillis() - start);
            }
            System.out.println(total);
        }
    }

    @SneakyThrows
    @Test
    void testExtractSubject() {
        var data = WmsaHome.getHomePath().resolve("test-data/");

        System.out.println("Running");

        var dict = new TermFrequencyDict(lm);

        SentenceExtractor se = new SentenceExtractor(lm);
        KeywordExtractor keywordExtractor = new KeywordExtractor();

        for (var file : Objects.requireNonNull(data.toFile().listFiles())) {
            System.out.println(file);
            var dld = se.extractSentences(Jsoup.parse(Files.readString(file.toPath())));
            Map<String, Integer> counts = new HashMap<>();
            for (var sentence : dld.sentences) {
                for (WordSpan kw : keywordExtractor.getProperNames(sentence)) {
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


    @SneakyThrows
    @Test
    @Disabled
    public void testSE() {
        var result = newSe.extractSentences(
                Jsoup.parse(Files.readString(Path.of("/home/vlofgren/man open (2) openat.html"))));

        var dict = new TermFrequencyDict(lm);
        System.out.println(new DocumentKeywordExtractor(dict).extractKeywords(result, new EdgeUrl("https://memex.marginalia.nu/")));
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
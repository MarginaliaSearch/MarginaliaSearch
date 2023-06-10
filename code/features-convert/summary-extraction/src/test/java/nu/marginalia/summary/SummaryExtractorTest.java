package nu.marginalia.summary;

import lombok.SneakyThrows;
import nu.marginalia.WmsaHome;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.summary.heuristic.*;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;

class SummaryExtractorTest {
    private SummaryExtractor summaryExtractor;
    private DocumentKeywordExtractor keywordExtractor;
    private SentenceExtractor setenceExtractor;

    @BeforeEach
    public void setUp() {
        keywordExtractor = new DocumentKeywordExtractor(new TermFrequencyDict(WmsaHome.getLanguageModels()));
        setenceExtractor = new SentenceExtractor(WmsaHome.getLanguageModels());

        summaryExtractor = new SummaryExtractor(255,
                new DomFilterHeuristic(255),
                new TagDensityHeuristic(255),
                new OpenGraphDescriptionHeuristic(),
                new MetaDescriptionHeuristic(),
                new FallbackHeuristic());
    }

    @SneakyThrows
    Set<String> getImportantWords(Document doc) {
        var dld = setenceExtractor.extractSentences(doc);
        var keywords = keywordExtractor.extractKeywords(dld, new EdgeUrl(
                "https://www.marginalia.nu/"
        ));
        System.out.println(keywords.importantWords);

        return keywords.importantWords;
    }

    @Test
    public void testTheRegister() throws IOException, URISyntaxException {
        String html = readClassPathFile("html/theregister.html");
        var doc = Jsoup.parse(html);



        var out = summaryExtractor.extractSummary(doc, getImportantWords(doc));
        System.out.println(out);

        var filter = new SummarizingDOMFilter();
        doc.filter(filter);

        filter.statistics.entrySet().stream().sorted(Comparator.comparing(e -> -e.getValue().textLength()))
                .filter(e -> e.getValue().textToTagRatio() > 0.75)
                .filter(e -> e.getValue().isElement())
                .filter(e -> e.getValue().textLength() > 32)
                .filter(e -> e.getValue().pos() < filter.cnt / 2.)
                .limit(5)
                .forEach(e -> {
                    System.out.println(e.getKey().nodeName() + ":" + e.getValue() + " / " + e.getValue().textToTagRatio());
                    System.out.println(e.getValue().text());
                });
    }

    @Test
    public void testSummaryFilter() throws IOException {
        String html = readClassPathFile("html/monadnock.html");
        var doc = Jsoup.parse(html);
        var filter = new SummarizingDOMFilter();
        doc.filter(filter);

        filter.statistics.entrySet().stream().sorted(Comparator.comparing(e -> -e.getValue().textLength()))
                .filter(e -> e.getValue().textToTagRatio() > 0.75)
                .filter(e -> e.getValue().isElement())
                .filter(e -> e.getValue().textLength() > 32)
                .filter(e -> e.getValue().pos() < filter.cnt / 2.)
                .limit(5)
                .forEach(e -> {
                    System.out.println(e.getKey().nodeName() + ":" + e.getValue() + " / " + e.getValue().textToTagRatio());
                    System.out.println(e.getValue().text());
                });
    }

    @Test
    void extractSurrey() throws IOException {
        String html = readClassPathFile("html/summarization/surrey.html");
        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc, getImportantWords(doc));


        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extractSurrey1() throws IOException {
        String html = readClassPathFile("html/summarization/surrey.html.1");
        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc, getImportantWords(doc));

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extract187() throws IOException {
        String html = readClassPathFile("html/summarization/187.shtml");
        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc, getImportantWords(doc));

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extractMonadnock() throws IOException {
        String html = readClassPathFile("html/monadnock.html");

        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc, getImportantWords(doc));

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    public void testWorkSet() throws IOException {
        var workSet = readWorkSet();
        workSet.forEach((path, str) -> {
            var doc = Jsoup.parse(str);
            String summary = summaryExtractor.extractSummary(doc, getImportantWords(doc));
            System.out.println(path + ": " + summary);
        });
    }
    private String readClassPathFile(String s) throws IOException {
        return new String(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream(s)).readAllBytes());
    }

    private Map<Path, String> readWorkSet() throws IOException {
        String index = readClassPathFile("html/work-set/index");
        String[] files = index.split("\n");

        Map<Path, String> result = new HashMap<>();
        for (String file : files) {
            Path p = Path.of("html/work-set/").resolve(file);

            result.put(p, readClassPathFile(p.toString()));
        }
        return result;
    }

}
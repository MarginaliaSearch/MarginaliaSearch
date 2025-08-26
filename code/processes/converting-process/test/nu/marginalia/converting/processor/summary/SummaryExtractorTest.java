package nu.marginalia.converting.processor.summary;

import nu.marginalia.WmsaHome;
import nu.marginalia.converting.processor.summary.heuristic.*;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.UnsupportedLanguageException;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;

@Tag("slow")
class SummaryExtractorTest {
    private SummaryExtractor summaryExtractor;
    private DocumentKeywordExtractor keywordExtractor;
    private SentenceExtractor setenceExtractor;

    @BeforeEach
    public void setUp() throws IOException, ParserConfigurationException, SAXException {
        keywordExtractor = new DocumentKeywordExtractor();
        setenceExtractor = new SentenceExtractor(new LanguageConfiguration(WmsaHome.getLanguageModels()), WmsaHome.getLanguageModels());

        summaryExtractor = new SummaryExtractor(255,
                new DomFilterHeuristic(255),
                new TagDensityHeuristic(255),
                new OpenGraphDescriptionHeuristic(),
                new MetaDescriptionHeuristic(),
                new FallbackHeuristic());
    }

    Set<String> getImportantWords(Document doc) throws URISyntaxException, UnsupportedLanguageException {
        var dld = setenceExtractor.extractSentences(doc);
        var keywords = keywordExtractor.extractKeywords(dld, new LinkTexts(), new EdgeUrl(
                "https://www.marginalia.nu/"
        ));
        System.out.println(keywords.importantWords);

        return keywords.importantWords;
    }

    @Test
    public void testTheRegister() throws IOException, URISyntaxException, UnsupportedLanguageException {
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
    void extractSurrey() throws IOException, URISyntaxException, UnsupportedLanguageException {
        String html = readClassPathFile("html/summarization/surrey.html");
        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc, getImportantWords(doc));


        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extractSurrey1() throws IOException, URISyntaxException, UnsupportedLanguageException {
        String html = readClassPathFile("html/summarization/surrey.html.1");
        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc, getImportantWords(doc));

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extract187() throws IOException, URISyntaxException, UnsupportedLanguageException {
        String html = readClassPathFile("html/summarization/187.shtml");
        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc, getImportantWords(doc));

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extractMonadnock() throws IOException, URISyntaxException, UnsupportedLanguageException {
        String html = readClassPathFile("html/monadnock.html");

        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc, getImportantWords(doc));

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    public void testWorkSet() throws IOException, URISyntaxException, UnsupportedLanguageException {
        var workSet = readWorkSet();
        for (Map.Entry<Path, String> entry : workSet.entrySet()) {
            final Path path = entry.getKey();
            final String str = entry.getValue();

            var doc = Jsoup.parse(str);
            String summary = summaryExtractor.extractSummary(doc, getImportantWords(doc));
            System.out.println(path + ": " + summary);
        }
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
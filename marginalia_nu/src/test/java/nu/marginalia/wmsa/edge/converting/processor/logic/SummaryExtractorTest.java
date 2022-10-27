package nu.marginalia.wmsa.edge.converting.processor.logic;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class SummaryExtractorTest {
    SummaryExtractor summaryExtractor;
    @BeforeEach
    public void setUp() {
        summaryExtractor = new SummaryExtractor(255);
    }

    @Test
    public void testSummaryFilter() throws IOException {
        String html = readClassPathFile("html/monadnock.html");
        var doc = Jsoup.parse(html);
        var filter = new SummaryExtractionFilter();
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
    public void testSummaryFilter3() throws IOException {
        var data = Path.of("/home/vlofgren/Code/tmp-data/url-327999153");
        String html = Files.readString(data);
        var doc = Jsoup.parse(html);
        var filter = new SummaryExtractionFilter();
        doc.filter(filter);

        filter.getSummary(255);
    }
    @Test
    public void testSummaryFilter2() throws IOException {
        var data = Path.of("/home/vlofgren/Code/tmp-data/");

        System.out.println("Running");

        var fos = new PrintWriter(new FileOutputStream("/tmp/summaryDiff.html"));
        fos.println("<table>");

        for (var file : Objects.requireNonNull(data.toFile().listFiles())) {

            var doc = Jsoup.parse(Files.readString(file.toPath()));
            fos.println("<tr><th colspan=2>" + file.getName() + "</th></tr>");
            fos.println("<tr><td width=50%>");
            var filter = new SummaryExtractionFilter();

            doc.select("header,nav,#header,#nav,#navigation,.header,.nav,.navigation,ul,li").remove();
            doc.filter(filter);
            var ret = filter.getSummary(255);

            fos.println(ret);
            fos.println("</td><td width=50%>");
            String summary = summaryExtractor.extractSummary(Jsoup.parse(Files.readString(file.toPath())));
            fos.println(summary);
            fos.println("</td></tr>");
        }

        fos.println("</table>");
        fos.flush();
    }

    @Test
    void extractSurrey() throws IOException {
        String html = readClassPathFile("html/summarization/surrey.html");
        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc);


        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extractSurrey1() throws IOException {
        String html = readClassPathFile("html/summarization/surrey.html.1");
        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc);

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extract187() throws IOException {
        String html = readClassPathFile("html/summarization/187.shtml");
        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc);

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extractMonadnock() throws IOException {
        String html = readClassPathFile("html/monadnock.html");

        var doc = Jsoup.parse(html);
        String summary = summaryExtractor.extractSummary(doc);

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    public void testWorkSet() throws IOException {
        var workSet = readWorkSet();
        workSet.forEach((path, str) -> {
            var doc = Jsoup.parse(str);
            String summary = summaryExtractor.extractSummary(doc);
            System.out.println(path + ": " + summary);
        });
    }
    private String readClassPathFile(String s) throws IOException {
        return new String(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream(s)).readAllBytes());
    }

    private Map<Path, String> readWorkSet() throws IOException {
        String index = readClassPathFile("html/work-set/index");
        String[] files = index.split("\n");

        Map<Path, String> result = new HashMap();
        for (String file : files) {
            Path p = Path.of("html/work-set/").resolve(file);

            result.put(p, readClassPathFile(p.toString()));
        }
        return result;
    }

}
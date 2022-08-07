package nu.marginalia.wmsa.edge.converting.processor.logic;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class SummaryExtractorTest {

    @Test
    void extractSurrey() throws IOException {
        String html = readClassPathFile("html/summarization/surrey.html");
        SummaryExtractor se = new SummaryExtractor(255);

        String summary = se.extractSummary(Jsoup.parse(html));

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extractSurrey1() throws IOException {
        String html = readClassPathFile("html/summarization/surrey.html.1");
        SummaryExtractor se = new SummaryExtractor(255);

        String summary = se.extractSummary(Jsoup.parse(html));

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extract187() throws IOException {
        String html = readClassPathFile("html/summarization/187.shtml");
        SummaryExtractor se = new SummaryExtractor(255);

        String summary = se.extractSummary(Jsoup.parse(html));

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    void extractMonadnock() throws IOException {
        String html = readClassPathFile("html/monadnock.html");
        SummaryExtractor se = new SummaryExtractor(255);

        String summary = se.extractSummary(Jsoup.parse(html));

        Assertions.assertFalse(summary.isBlank());

        System.out.println(summary);
    }

    @Test
    public void testWorkSet() throws IOException {
        var workSet = readWorkSet();
        SummaryExtractor se = new SummaryExtractor(255);
        workSet.forEach((path, str) -> {
            String summary = se.extractSummary(Jsoup.parse(str));
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
package nu.marginalia.converting.processor.plugin.specialization;

import nu.marginalia.converting.model.DocumentHeaders;
import nu.marginalia.converting.processor.logic.DocumentGeneratorExtractor;
import nu.marginalia.converting.processor.logic.TitleExtractor;
import nu.marginalia.converting.processor.summary.SummaryExtractor;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.test.CommonTestData;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.Set;

class XenForoSpecializationTest {

    static XenForoSpecialization specialization;
    static DocumentGeneratorExtractor generatorExtractor = new DocumentGeneratorExtractor();

    String thread = CommonTestData.loadTestData("mock-crawl-data/xenforo/thread.html");

    @BeforeAll
    public static void setUpAll() {
        specialization = new XenForoSpecialization(
                new SummaryExtractor(255,
                        null,
                        null,
                        null,
                        null,
                        null),
                new TitleExtractor(128)
                );
    }

    @Test
    void prune() {
        System.out.println(specialization.prune(Jsoup.parse(thread)));
    }

    @Test
    void generatorExtraction() throws URISyntaxException {
        var gen = generatorExtractor.detectGenerator(new EdgeUrl("https://www.example.com/"), Jsoup.parse(thread), new DocumentHeaders(""));

        System.out.println(gen);
    }

    @Test
    void getSummary() {
        String summary = specialization.getSummary(Jsoup.parse(thread), Set.of(""));

        System.out.println(summary);
    }
}
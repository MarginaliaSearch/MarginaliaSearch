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

import java.util.Set;

class GogSpecializationTest {

    static GogStoreSpecialization specialization;
    static DocumentGeneratorExtractor generatorExtractor = new DocumentGeneratorExtractor();

    String storePage = CommonTestData.loadTestData("html/gog-store.html");

    @BeforeAll
    public static void setUpAll() {
        specialization = new GogStoreSpecialization(
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
        System.out.println(specialization.prune(Jsoup.parse(storePage)));
    }

    @Test
    void generatorExtraction() throws Exception {
        var gen = generatorExtractor.detectGenerator(new EdgeUrl("https://www.example.com/"), Jsoup.parse(storePage), new DocumentHeaders(""));

        System.out.println(gen);
    }

    @Test
    void getSummary() {
        String summary = specialization.getSummary(Jsoup.parse(storePage), Set.of(""));

        System.out.println(summary);
    }
}
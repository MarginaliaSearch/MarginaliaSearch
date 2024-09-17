package nu.marginalia.converting.processor.plugin.specialization;

import nu.marginalia.converting.processor.logic.DocumentGeneratorExtractor;
import nu.marginalia.converting.processor.summary.SummaryExtractor;
import nu.marginalia.test.CommonTestData;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

class JavadocSpecializationTest {

    static JavadocSpecialization specialization;
    static DocumentGeneratorExtractor generatorExtractor = new DocumentGeneratorExtractor();

    String thread = CommonTestData.loadTestData("mock-crawl-data/javadoc/stream.html");

    @BeforeAll
    public static void setUpAll() {
        specialization = new JavadocSpecialization(
                new SummaryExtractor(255,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    void prune() {
        System.out.println(specialization.prune(Jsoup.parse(thread)));
    }

    @Test
    void generatorExtraction() {
        var gen = generatorExtractor.detectGenerator(Jsoup.parse(thread), "");

        System.out.println(gen);
    }

    @Test
    void getSummary() {
        String summary = specialization.getSummary(Jsoup.parse(thread), Set.of(""));

        System.out.println(summary);
    }
}
package nu.marginalia.converting.processor.plugin.specialization;

import nu.marginalia.converting.model.DocumentHeaders;
import nu.marginalia.converting.processor.logic.DocumentGeneratorExtractor;
import nu.marginalia.converting.processor.summary.SummaryExtractor;
import nu.marginalia.test.CommonTestData;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

class LemmySpecializationTest {

    static LemmySpecialization specialization;
    static DocumentGeneratorExtractor generatorExtractor = new DocumentGeneratorExtractor();

    String lemmyIndexHtml = CommonTestData.loadTestData("mock-crawl-data/lemmy/index.html");
    String lemmyPost = CommonTestData.loadTestData("mock-crawl-data/lemmy/108995.html");
    String lemmyIndexC = CommonTestData.loadTestData("mock-crawl-data/lemmy/c_startrek.html");

    @BeforeAll
    public static void setUpAll() {
        specialization = new LemmySpecialization(
                new SummaryExtractor(255,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    void prune() {
        System.out.println(specialization.prune(Jsoup.parse(lemmyIndexHtml)));
        System.out.println(specialization.prune(Jsoup.parse(lemmyPost)));
    }

    @Test
    void generatorExtraction() {
        var generatorIndex = generatorExtractor.detectGenerator(Jsoup.parse(lemmyIndexHtml), new DocumentHeaders(""));
        var generatorPost = generatorExtractor.detectGenerator(Jsoup.parse(lemmyPost), new DocumentHeaders(""));

        System.out.println(generatorIndex);
        System.out.println(generatorPost);
    }
    @Test
    void getSummary() {
        String summaryPost = specialization.getSummary(Jsoup.parse(lemmyPost), Set.of(""));
        String summaryIndex = specialization.getSummary(Jsoup.parse(lemmyIndexHtml), Set.of(""));
        String summaryC = specialization.getSummary(Jsoup.parse(lemmyIndexC), Set.of(""));

        System.out.println(summaryPost);
        System.out.println(summaryIndex);
        System.out.println(summaryC);
    }
}
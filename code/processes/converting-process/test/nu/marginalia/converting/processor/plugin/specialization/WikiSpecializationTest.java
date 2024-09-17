package nu.marginalia.converting.processor.plugin.specialization;

import nu.marginalia.converting.processor.summary.SummaryExtractor;
import nu.marginalia.test.CommonTestData;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

class WikiSpecializationTest {

    static WikiSpecialization specialization;

    String doomArmor = CommonTestData.loadTestData("mock-crawl-data/mediawiki/doom2.html");
    String doomFan = CommonTestData.loadTestData("mock-crawl-data/mediawiki/doom1.html");

    @BeforeAll
    public static void setUpAll() {
        specialization = new WikiSpecialization(
                new SummaryExtractor(255,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    void prune() {
        System.out.println(specialization.prune(Jsoup.parse(doomFan)));
        System.out.println(specialization.prune(Jsoup.parse(doomArmor)));
    }

    @Test
    void generatorExtraction() {
    }

    @Test
    void getSummary() {
        System.out.println(specialization.getSummary(Jsoup.parse(doomArmor), Set.of("")));
        System.out.println(specialization.getSummary(Jsoup.parse(doomFan), Set.of("")));
    }
}
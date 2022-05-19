package nu.marginalia.wmsa.edge.crawler.domain;

import nu.marginalia.wmsa.edge.crawler.domain.language.LanguageFilter;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LanguageFilterTest {

    @Test
    void isPageInteresting() {
        var languageFilter = new LanguageFilter();
        assertTrue(languageFilter.isPageInterestingByHtmlTag(Jsoup.parse("<html></html>")).orElse(true));
        assertTrue(languageFilter.isPageInterestingByHtmlTag(Jsoup.parse("<html lang=\"en\"></html>")).orElse(false));
        assertFalse(languageFilter.isPageInterestingByHtmlTag(Jsoup.parse("<html lang=\"no\"></html>")).orElse(false));
    }

    @Test
    public void isStringChinsese() {
        var languageFilter = new LanguageFilter();
        assertTrue(languageFilter.isBlockedUnicodeRange("溶岩ドームの手前に広がる斜面（木が生えているところ）は普賢岳の山体です．今回の噴火にともない，このあたりの山体がマグマに押されて変形し，北（写真では左）にむかって100mほどせりだしました\n"));
    }

}
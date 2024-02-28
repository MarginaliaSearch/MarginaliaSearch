package nu.marginalia.language.filter;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageFilterTest {

    @Test
    void isPageInteresting() {
        var languageFilter = new LanguageFilter(TestLanguageModels.getLanguageModels());
        assertTrue(languageFilter.isPageInterestingByHtmlTag(Jsoup.parse("<html></html>")).orElse(true));
        assertTrue(languageFilter.isPageInterestingByHtmlTag(Jsoup.parse("<html lang=\"en\"></html>")).orElse(false));
        assertFalse(languageFilter.isPageInterestingByHtmlTag(Jsoup.parse("<html lang=\"no\"></html>")).orElse(false));
    }

}
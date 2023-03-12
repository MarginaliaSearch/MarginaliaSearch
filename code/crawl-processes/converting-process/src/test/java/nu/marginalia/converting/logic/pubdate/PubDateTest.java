package nu.marginalia.converting.logic.pubdate;

import nu.marginalia.model.crawl.PubDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PubDateTest {

    @Test
    void yearByte() {
        for (int year = PubDate.MIN_YEAR; year < 2022; year++) {
            var pdInstance = new PubDate(null, year);
            assertEquals(year, PubDate.fromYearByte(pdInstance.yearByte()));
        }
    }


}
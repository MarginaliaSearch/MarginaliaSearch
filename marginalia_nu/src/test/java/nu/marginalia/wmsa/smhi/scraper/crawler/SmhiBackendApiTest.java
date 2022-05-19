package nu.marginalia.wmsa.smhi.scraper.crawler;

import nu.marginalia.wmsa.smhi.model.Plats;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class SmhiBackendApiTest {

    @Test
    void hamtaData() throws Exception {
        var api = new SmhiBackendApi("nu.marginalia");


        System.out.println(api.hamtaData(new Plats("Ystad", "55.42966", "13.82041"))
                .jsonContent
        );
    }

    @Test
    public void testDatum() {
        System.out.println(LocalDateTime.parse("2021-05-29T14:06:48Z",
                DateTimeFormatter.ISO_ZONED_DATE_TIME)
                .atZone(ZoneId.of("GMT"))
                .toOffsetDateTime()
                .atZoneSameInstant(ZoneId.of("Europe/Stockholm"))
        );
    }
}
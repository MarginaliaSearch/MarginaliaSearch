package nu.marginalia.index.model;

import nu.marginalia.index.config.RankingSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingSettingsTest {

    Path tempFile;
    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile(getClass().getSimpleName(), ".tmp");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.delete(tempFile);
    }

    @Test
    void testParseRankingSettings() throws IOException {
        Files.writeString(tempFile, """
                                        retro:
                                          max: 50
                                          domains:
                                          - "www.rep.routledge.com"
                                          - "www.personal.kent.edu"
                                        small:
                                          max: 10
                                          domains:
                                          - "bikobatanari.art"
                                          - "wiki.xxiivv.com"
                                        academia:
                                          max: 101
                                          domains:
                                          - "%edu"
                                        standard:
                                          max: 23
                                          domains:
                                          - "memex.marginalia.nu"
                                        """);

        var settings = RankingSettings.from(tempFile);
        assertEquals(List.of("www.rep.routledge.com","www.personal.kent.edu"), settings.retro.domains);
        assertEquals(50, settings.retro.max);
        assertEquals(List.of("bikobatanari.art","wiki.xxiivv.com"), settings.small.domains);
        assertEquals(10, settings.small.max);
        assertEquals(List.of("bikobatanari.art","wiki.xxiivv.com"), settings.small.domains);
        assertEquals(List.of("%edu"), settings.academia.domains);
        assertEquals(List.of("memex.marginalia.nu"), settings.standard.domains);

    }
}
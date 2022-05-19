package nu.marginalia.wmsa.edge.index.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
                                        - "www.rep.routledge.com"
                                        - "www.personal.kent.edu"
                                        small:
                                        - "bikobatanari.art"
                                        - "wiki.xxiivv.com"
                                        academia:
                                        - "%edu"
                                        standard:
                                        - "memex.marginalia.nu"
                                        """);

        var settings = RankingSettings.from(tempFile);
        assertEquals(List.of("www.rep.routledge.com","www.personal.kent.edu"), settings.retro);
        assertEquals(List.of("bikobatanari.art","wiki.xxiivv.com"), settings.small);
        assertEquals(List.of("%edu"), settings.academia);
        assertEquals(List.of("memex.marginalia.nu"), settings.standard);

    }
}
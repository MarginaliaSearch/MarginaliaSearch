package nu.marginalia.language.config;

import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.language.filter.TestLanguageModels;
import nu.marginalia.language.pos.PosPattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LanguageConfigurationTest {
    private static LanguageConfiguration languageConfiguration;

    @BeforeAll
    public static void setUpAll() throws IOException, SAXException, ParserConfigurationException {
        languageConfiguration = new LanguageConfiguration(TestLanguageModels.getLanguageModels());
    }

    @Test
    void testBasic() {
        Assertions.assertNotNull(languageConfiguration.getLanguage("en"));
        Assertions.assertNotNull(languageConfiguration.getLanguage("sv"));
        Assertions.assertNotNull(languageConfiguration.getLanguage("xx"));
        Assertions.assertNull(languageConfiguration.getLanguage("!!"));
    }

    @Test
    public void testStemming() {
        var svStemmer = languageConfiguration.getLanguage("sv").stemmer();
        var enStemmer = languageConfiguration.getLanguage("en").stemmer();

        Assertions.assertNotNull(svStemmer);
        Assertions.assertNotNull(enStemmer);

        assertEquals("bil", svStemmer.stem("bilar"));
        assertEquals("dogged", svStemmer.stem("dogged"));
        assertEquals("bilar", enStemmer.stem("bilar"));
        assertEquals("dog", enStemmer.stem("dogged"));
    }

    @Test
    public void testPosData() {
        var svPos = languageConfiguration.getLanguage("sv").posTagger();
        var enPos = languageConfiguration.getLanguage("en").posTagger();

        Assertions.assertNotNull(svPos);
        Assertions.assertNotNull(enPos);

        System.out.println(enPos);
        System.out.println(svPos);

        Assertions.assertNotEquals(svPos.tagDict, enPos.tagDict);
    }

    @Test
    public void testPosPattern() {
        var enPos = languageConfiguration.getLanguage("en").posTagger();

        System.out.println(new PosPattern(enPos, "NNP").pattern);
        System.out.println(new PosPattern(enPos, "NNP").pattern);
        System.out.println(new PosPattern(enPos, "NNP NNPS").pattern);
        System.out.println(new PosPattern(enPos, "NNPS (NNPS DT) DT").pattern);
        System.out.println(new PosPattern(enPos,
                "(NNP NNPS) (NNP NNPS IN DT CC) (NNP NNPS IN DT CC) (NNP NNPS)").pattern);

        assertEquals(new PosPattern(enPos, "NNP*").pattern,
                new PosPattern(enPos, "(NNP NNPS)").pattern);
        assertEquals(LongList.of(0L), new PosPattern(enPos, "Hello").pattern);
        assertEquals(0, (new PosPattern(enPos, "(NNP NNPS)").pattern.getFirst() & new PosPattern(enPos, "!(NNP NNPS)").pattern.getFirst()));
        assertEquals(new PosPattern(enPos, "(NNP NNPS)").pattern.getFirst().longValue(), new PosPattern(enPos, "*").pattern.getFirst() ^ new PosPattern(enPos, "!(NNP NNPS)").pattern.getFirst());
    }
}


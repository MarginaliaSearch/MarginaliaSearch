package nu.marginalia.language.config;

import nu.marginalia.language.filter.TestLanguageModels;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LanguageConfigurationTest {
    static LanguageConfiguration languageConfiguration;

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
}
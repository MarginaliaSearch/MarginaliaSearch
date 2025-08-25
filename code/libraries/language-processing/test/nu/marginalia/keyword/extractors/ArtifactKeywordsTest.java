package nu.marginalia.keyword.extractors;

import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.util.TestLanguageModels;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactKeywordsTest {

    @Test
    public void testExtractArtifacts() throws IOException, ParserConfigurationException, SAXException {
        SentenceExtractor se = new SentenceExtractor(new LanguageConfiguration(TestLanguageModels.getLanguageModels()), TestLanguageModels.getLanguageModels());

        var artifacts = new ArtifactKeywords(se.extractSentences("Hello I'm <vlofgren@marginalia.nu>, what's up?", "hello!"));
        System.out.println(artifacts.getWords());
        assertTrue(artifacts.getWords().contains("vlofgren"));
        assertTrue(artifacts.getWords().contains("marginalia.nu"));
        assertTrue(artifacts.getWords().contains("@marginalia.nu"));
        assertTrue(artifacts.getWords().contains("vlofgren@marginalia.nu"));
    }
}
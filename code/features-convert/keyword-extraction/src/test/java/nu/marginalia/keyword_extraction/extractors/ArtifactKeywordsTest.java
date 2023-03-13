package nu.marginalia.keyword_extraction.extractors;

import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.test.util.TestLanguageModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactKeywordsTest {

    @Test
    public void testExtractArtifacts() {
        SentenceExtractor se = new SentenceExtractor(TestLanguageModels.getLanguageModels());

        var artifacts = new ArtifactKeywords(se.extractSentences("Hello I'm <vlofgren@marginalia.nu>, what's up?", "hello!"));
        System.out.println(artifacts.getWords());
        assertTrue(artifacts.getWords().contains("vlofgren"));
        assertTrue(artifacts.getWords().contains("marginalia.nu"));
        assertTrue(artifacts.getWords().contains("@marginalia.nu"));
        assertTrue(artifacts.getWords().contains("vlofgren@marginalia.nu"));
    }
}
package nu.marginalia.wmsa.edge.search.query;

import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.util.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.assistant.dict.NGramBloomFilter;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class QueryVariantsTest {
    QueryVariants variants;
    QueryParser parser;
    SentenceExtractor se;

    @BeforeEach
    public void setUp() throws IOException {
        LanguageModels lm = TestLanguageModels.getLanguageModels();

        se  = new SentenceExtractor(lm);

        var dict = new TermFrequencyDict(lm);
        var ngrams = new NGramBloomFilter(lm);
        variants = new QueryVariants(lm, dict, ngrams, new EnglishDictionary(dict));
        parser = new QueryParser(new EnglishDictionary(dict), variants);
    }

    @Test
    void getQueryFood() {
        System.out.println(se.extractSentence("we are alone"));
        testCase("Omelet recipe");
    }

    @Test
    void getQueryVariants() {
        System.out.println(se.extractSentence("we are alone"));
        testCase("DOS");
        testCase("dos");
        testCase("we are alone");
        testCase("3D Realms");
        testCase("I am alone");
        testCase("plato cave");
        testCase("The internet is dead");

        testCase("TRS80");
        testCase("TRS-80");
        testCase("TRS-80");
        testCase("Raspberry Pi 2");
        testCase("Duke Nukem 3D");
        testCase("The Man of Tomorrow");
        testCase("Computer Manual");
        testCase("Knitting");
        testCase("capcom");
        testCase("the man of tomorrow");
    }

    private void testCase(String input) {
        var tokens = variants.getQueryVariants(parser.extractBasicTokens(input));
        System.out.println(tokens);
    }
}
package nu.marginalia.query_parser;

import nu.marginalia.LanguageModels;
import nu.marginalia.language.statistics.EnglishDictionary;
import nu.marginalia.language.statistics.NGramBloomFilter;
import nu.marginalia.language.statistics.TermFrequencyDict;
import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.language.sentence.SentenceExtractor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class QueryVariantsTest {
    static QueryVariants variants;
    static QueryParser parser;
    static SentenceExtractor se;

    @BeforeAll
    public static void setUp() throws IOException {
        LanguageModels lm = TestLanguageModels.getLanguageModels();

        se  = new SentenceExtractor(lm);

        var dict = new TermFrequencyDict(lm);
        var ngrams = new NGramBloomFilter(lm);
        variants = new QueryVariants(lm, dict, ngrams, new EnglishDictionary(dict));
        parser = new QueryParser();
    }

    @Test
    void getQueryFood() {
        System.out.println(se.extractSentence("we are alone"));
        testCase("Omelet recipe");
    }

    @Test
    void queryNegation() {
        System.out.println(se.extractSentence("salt lake -city"));
        testCase("salt lake -city");
    }


    @Test
    void getQueryVariants() {
        System.out.println(se.extractSentence("we are alone"));
        testCase("inside job reviews");
        testCase("plato apology");
        testCase("mechanical keyboard");
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
        var tokens = variants.getQueryVariants(parser.parse(input));
        System.out.println(tokens);
    }
}
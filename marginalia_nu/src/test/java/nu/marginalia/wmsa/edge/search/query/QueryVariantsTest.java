package nu.marginalia.wmsa.edge.search.query;

import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class QueryVariantsTest {
    QueryVariants variants;
    QueryParser parser;
    SentenceExtractor se;
    @BeforeEach
    public void setUp() {
        LanguageModels lm = TestLanguageModels.getLanguageModels();

        se  = new SentenceExtractor(lm);

        var dict = new NGramDict(lm);
        variants = new QueryVariants(lm, dict, new EnglishDictionary(dict));
        parser = new QueryParser(new EnglishDictionary(dict), variants);
    }

    @Test
    void getQueryVariants() {
        System.out.println(se.extractSentence("we are alone"));
        testCase("DOS", List.of("DOS"));
        testCase("dos", List.of("dos"));
        testCase("we are alone", List.of("dos"));
        testCase("3D Realms", List.of("dos"));
        testCase("I am alone", List.of("dos"));
        testCase("plato cave", List.of("dos"));
        testCase("The internet is dead", List.of("dos"));

        testCase("TRS80", List.of("trs_80"), List.of("trs80"));
        testCase("TRS-80", List.of("trs-80"), List.of("trs80"));
        testCase("TRS-80", List.of("trs-80"), List.of("trs80"));
        testCase("Raspberry Pi 2", List.of("trs-80"), List.of("trs80"));
        testCase("Duke Nukem 3D", List.of("trs-80"), List.of("trs80"));
        testCase("The Man of Tomorrow", List.of("trs-80"), List.of("trs80"));
        testCase("Computer Manual", List.of("trs-80"), List.of("trs80"));
        testCase("Knitting", List.of("trs-80"), List.of("trs80"));
        testCase("capcom", List.of("trs-80"), List.of("trs80"));
        testCase("the man of tomorrow", List.of("trs-80"), List.of("trs80"));
    }

    private void testCase(String input, List<String>... expected) {
        var tokens = variants.getQueryVariants(parser.extractBasicTokens(input));
        System.out.println(tokens);
//        var result = tokens.stream().map(lst -> lst.terms).collect(Collectors.toSet());
//        assertEquals(Set.of(expected), result, "Case failed: " + input);
    }
}
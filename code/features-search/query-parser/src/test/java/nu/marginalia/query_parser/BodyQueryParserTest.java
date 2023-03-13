package nu.marginalia.query_parser;

import nu.marginalia.LanguageModels;
import nu.marginalia.language.statistics.EnglishDictionary;
import nu.marginalia.ngram_bloom_filter.NGramBloomFilter;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import nu.marginalia.query_parser.token.TokenType;
import nu.marginalia.util.TestLanguageModels;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BodyQueryParserTest {
    private QueryParser parser;
    private static TermFrequencyDict dict;
    private static EnglishDictionary englishDictionary;
    private static NGramBloomFilter nGramBloomFilter;
    private static final LanguageModels lm = TestLanguageModels.getLanguageModels();
    private QueryPermutation permutation;

    @BeforeAll
    public static void init() throws IOException {
        dict = new TermFrequencyDict(lm);
        nGramBloomFilter = new NGramBloomFilter(lm);
        englishDictionary = new EnglishDictionary(dict);
    }

    @BeforeEach
    public void setUp() {
        parser = new QueryParser();
        permutation = new QueryPermutation(new QueryVariants(lm, dict, nGramBloomFilter, englishDictionary));
    }

    @Test
    public void testTitleMatcher() {
        List<String> terms = List.of("3d", "realms");
        assertEquals(2, terms.stream().map(String::toLowerCase).filter("3D Realms Site: Forums".toLowerCase()::contains).count());
    }
    @Test
    void parseSimple() {
        var results = parser.parse("hello");
        results.forEach(System.out::println);
        assertEquals(1, results.size());
        assertEquals(TokenType.LITERAL_TERM, results.get(0).type);
        assertEquals("hello", results.get(0).str);
    }

    @Test
    void parseQuotes() {
        var results = parser.parse("\u201Chello world\u201D");
        results.forEach(System.out::println);
        assertEquals(TokenType.QUOT_TERM, results.get(0).type);
        assertEquals("hello_world", results.get(0).str);
        assertEquals("\"hello world\"", results.get(0).displayStr);
    }

    @Test
    void parseExclude() {
        var results = parser.parse("-Hello");
        results.forEach(System.out::println);
        assertEquals(TokenType.EXCLUDE_TERM, results.get(0).type);
        assertEquals("hello", results.get(0).str);
        assertEquals("-hello", results.get(0).displayStr);
    }

    @Test
    void parseNear() {
        var results = parser.parse("near:memex.marginalia.nu");
        results.forEach(System.out::println);
        assertEquals(TokenType.NEAR_TERM, results.get(0).type);
        assertEquals("memex.marginalia.nu", results.get(0).str);
        assertEquals("near:memex.marginalia.nu", results.get(0).displayStr);
    }

    @Test
    void parseCombined() {
        for (var list : permutation.permuteQueries(parser.parse("dune 2 remake"))) {
            for (var t: list) {
                System.out.printf("%s ", t.str);
            }
            System.out.println();
        }
    }
    @Test
    void parseCombinedDOS() {
        for (var list : permutation.permuteQueries(parser.parse("ab ba baa abba baba ab ba"))) {
            for (var t: list) {
                System.out.printf("%s ", t.str);
            }
            System.out.println();
        }
    }

    @Test
    void parseCombinedSuperman() {
        for (var list : permutation.permuteQueries(parser.parse("wizardry proving grounds of the mad overlord"))) {
            for (var t: list) {
                System.out.printf("%s ", t.str);
            }
            System.out.println();
        }
    }
    @Test
    void testEdgeCases() {
        parser.parse("site:localhost 3D").forEach(System.out::println);
        parser.parse("-wolfenstein 3D").forEach(System.out::println);
        parser.parse("-wolfenstein 3D \"").forEach(System.out::println);
    }


}
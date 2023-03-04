package nu.marginalia.query_parser;

import nu.marginalia.LanguageModels;
import nu.marginalia.language.statistics.EnglishDictionary;
import nu.marginalia.language.statistics.NGramBloomFilter;
import nu.marginalia.language.statistics.TermFrequencyDict;
import nu.marginalia.query_parser.token.Token;
import nu.marginalia.query_parser.token.TokenType;
import nu.marginalia.util.TestLanguageModels;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryParserTest {
    private static QueryParser parser;
    private static TermFrequencyDict dict;
    private static EnglishDictionary englishDictionary;
    private static NGramBloomFilter nGramBloomFilter;
    private static final LanguageModels lm = TestLanguageModels.getLanguageModels();

    @BeforeAll
    public static void setUp() throws IOException {
        dict = new TermFrequencyDict(lm);
        nGramBloomFilter = new NGramBloomFilter(lm);
        englishDictionary = new EnglishDictionary(dict);

        parser = new QueryParser();
    }

    @Test
    public void testAdviceString() {
        var ret = parser.parse("alcibiades (plato) \"my query\" -cars");
        assertEquals(4, ret.size());

        var alcibiades = ret.get(0);
        assertEquals(TokenType.LITERAL_TERM, alcibiades.type);
        assertEquals("alcibiades", alcibiades.str);
        assertEquals("alcibiades", alcibiades.displayStr);

        var plato = ret.get(1);
        assertEquals(TokenType.ADVICE_TERM, plato.type);
        assertEquals("plato", plato.str);
        assertEquals("(plato)", plato.displayStr);

        var my_query = ret.get(2);
        assertEquals(TokenType.QUOT_TERM, my_query.type);
        assertEquals("my_query", my_query.str);
        assertEquals("\"my query\"", my_query.displayStr);

        var not_cars = ret.get(3);
        assertEquals(TokenType.EXCLUDE_TERM, not_cars.type);
        assertEquals("cars", not_cars.str);
        assertEquals("-cars", not_cars.displayStr);
    }

    @Test
    public void testParseYear() {
        System.out.println(parser.parse("year>2000"));
        System.out.println(parser.parse("year=2000"));
        System.out.println(parser.parse("year<2000"));
    }

    @Test
    public void testNonAsciiNames() {
        verifyParseResult("André the Giant", "andre", "the", "giant");
        verifyParseResult("Stanisław Lem", "stanislaw", "lem");
        verifyParseResult("Nicolae Ceaușescu", "nicolae", "ceausescu");
        verifyParseResult("Þorrablót", "thorrablot");
        verifyParseResult("Karolis Koncevičius", "karolis", "koncevicius");
    }

    private void verifyParseResult(String query, String... expectedTokens) {
        assertArrayEquals(expectedTokens, getTokenStrings(parser.parse(query)));
    }
    private String[] getTokenStrings(List<Token> tokens) {
        return tokens.stream().map(t -> t.str).toArray(String[]::new);
    }

}
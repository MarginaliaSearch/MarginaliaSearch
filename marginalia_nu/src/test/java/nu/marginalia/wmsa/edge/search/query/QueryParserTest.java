package nu.marginalia.wmsa.edge.search.query;

import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.util.language.conf.LanguageModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

class QueryParserTest {
    private QueryParser parser;
    private static NGramDict dict;
    private static EnglishDictionary englishDictionary;
    private static final LanguageModels lm = TestLanguageModels.getLanguageModels();

    @BeforeEach
    public void setUp() {
        dict = new NGramDict(lm);
        englishDictionary = new EnglishDictionary(dict);

        parser = new QueryParser(englishDictionary, new QueryVariants(lm, dict, englishDictionary));
    }

    @Test
    void variantQueries() {
        var r = parser.parse("car stemming");
        parser.variantQueries(r).forEach(query -> {
            System.out.println(query.stream().map(t -> t.str).collect(Collectors.joining(", ")));
        });
    }
}
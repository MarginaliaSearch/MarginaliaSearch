package nu.marginalia.functions.searchquery.query_parser;

import nu.marginalia.WmsaHome;
import nu.marginalia.functions.searchquery.query_parser.token.QueryToken;
import nu.marginalia.language.config.LanguageConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;

class QueryParserTest {

    LanguageConfiguration languageConfiguration = new LanguageConfiguration(WmsaHome.getLanguageModels());

    QueryParserTest() throws IOException, ParserConfigurationException, SAXException {
    }

    @Test
    // https://github.com/MarginaliaSearch/MarginaliaSearch/issues/140
    void parse__builtin_ffs() {
        QueryParser parser = new QueryParser();
        var tokens = parser.parse(languageConfiguration.getLanguage("en"), "__builtin_ffs");
        Assertions.assertEquals(List.of(new QueryToken.LiteralTerm("builtin_ffs", "__builtin_ffs")), tokens);
    }

    @Test
    void trailingParens() {
        QueryParser parser = new QueryParser();
        var tokens = parser.parse(languageConfiguration.getLanguage("en"), "strcpy()");
        Assertions.assertEquals(List.of(new QueryToken.LiteralTerm("strcpy", "strcpy()")), tokens);
    }

    @Test
    void trailingQuote() {
        QueryParser parser = new QueryParser();
        var tokens = parser.parse(languageConfiguration.getLanguage("en"), "bob's");
        Assertions.assertEquals(List.of(new QueryToken.LiteralTerm("bob", "bob's")), tokens);
    }
}
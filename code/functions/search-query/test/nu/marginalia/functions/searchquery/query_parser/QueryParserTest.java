package nu.marginalia.functions.searchquery.query_parser;

import nu.marginalia.functions.searchquery.query_parser.token.QueryToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class QueryParserTest {

    @Test
    // https://github.com/MarginaliaSearch/MarginaliaSearch/issues/140
    void parse__builtin_ffs() {
        QueryParser parser = new QueryParser();
        var tokens = parser.parse("__builtin_ffs");
        Assertions.assertEquals(List.of(new QueryToken.LiteralTerm("builtin_ffs", "__builtin_ffs")), tokens);
    }

    @Test
    void trailingParens() {
        QueryParser parser = new QueryParser();
        var tokens = parser.parse("strcpy()");
        Assertions.assertEquals(List.of(new QueryToken.LiteralTerm("strcpy", "strcpy()")), tokens);
    }

    @Test
    void trailingQuote() {
        QueryParser parser = new QueryParser();
        var tokens = parser.parse("bob's");
        Assertions.assertEquals(List.of(new QueryToken.LiteralTerm("bob", "bob's")), tokens);
    }
}
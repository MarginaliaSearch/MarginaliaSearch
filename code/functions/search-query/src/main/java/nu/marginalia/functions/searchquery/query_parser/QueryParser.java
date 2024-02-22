package nu.marginalia.functions.searchquery.query_parser;

import nu.marginalia.language.WordPatterns;
import nu.marginalia.functions.searchquery.query_parser.token.Token;
import nu.marginalia.functions.searchquery.query_parser.token.TokenType;
import nu.marginalia.util.transform_list.TransformList;

import java.util.List;

public class QueryParser {

    private final QueryTokenizer tokenizer = new QueryTokenizer();

    public List<Token> parse(String query) {
        List<Token> basicTokens = tokenizer.tokenizeQuery(query);

        TransformList<Token> list = new TransformList<>(basicTokens);

        list.transformEach(QueryParser::handleQuoteTokens);
        list.transformEach(QueryParser::trimLiterals);
        list.transformEachPair(QueryParser::createNegatedTerms);
        list.transformEachPair(QueryParser::createPriorityTerms);
        list.transformEach(QueryParser::handleSpecialOperations);
        list.scanAndTransform(TokenType.LPAREN, TokenType.RPAREN, QueryParser::handleAdvisoryTerms);

        return list.getBackingList();
    }

    private static void handleQuoteTokens(TransformList<Token>.Entity entity) {
        var t = entity.value();
        if (t.type == TokenType.QUOT) {
            entity.replace(new Token(TokenType.QUOT_TERM,
                    t.str.replaceAll("\\s+", WordPatterns.WORD_TOKEN_JOINER),
                    t.displayStr));
        }
    }

    private static void trimLiterals(TransformList<Token>.Entity entity) {
        var t = entity.value();

        if (t.type == TokenType.LITERAL_TERM
                && (t.str.endsWith(":") || t.str.endsWith("."))
                && t.str.length() > 1) {
            entity.replace(new Token(TokenType.LITERAL_TERM, t.str.substring(0, t.str.length() - 1), t.displayStr));
        }

    }

    private static void createNegatedTerms(TransformList<Token>.Entity first, TransformList<Token>.Entity second) {
        var t = first.value();
        var tn = second.value();

        if (t.type == TokenType.MINUS && tn.type == TokenType.LITERAL_TERM) {
            first.remove();
            second.replace(new Token(TokenType.EXCLUDE_TERM, tn.str, "-" + tn.str));
        }
    }

    private static void createPriorityTerms(TransformList<Token>.Entity first, TransformList<Token>.Entity second) {
        var t = first.value();
        var tn = second.value();

        if (t.type == TokenType.QMARK && tn.type == TokenType.LITERAL_TERM) {
            first.remove();
            second.replace(new Token(TokenType.PRIORTY_TERM, tn.str, "?" + tn.str));
        }
    }

    private static void handleSpecialOperations(TransformList<Token>.Entity entity) {
        var t = entity.value();
        if (t.type != TokenType.LITERAL_TERM) {
            return;
        }

        if (t.str.startsWith("q") && t.str.matches("q[=><]\\d+")) {
            entity.replace(new Token(TokenType.QUALITY_TERM, t.str.substring(1), t.displayStr));
        } else if (t.str.startsWith("near:")) {
            entity.replace(new Token(TokenType.NEAR_TERM, t.str.substring(5), t.displayStr));
        } else if (t.str.startsWith("year") && t.str.matches("year[=><]\\d{4}")) {
            entity.replace(new Token(TokenType.YEAR_TERM, t.str.substring(4), t.displayStr));
        } else if (t.str.startsWith("size") && t.str.matches("size[=><]\\d+")) {
            entity.replace(new Token(TokenType.SIZE_TERM, t.str.substring(4), t.displayStr));
        } else if (t.str.startsWith("rank") && t.str.matches("rank[=><]\\d+")) {
            entity.replace(new Token(TokenType.RANK_TERM, t.str.substring(4), t.displayStr));
        } else if (t.str.startsWith("count") && t.str.matches("count[=><]\\d+")) {
            entity.replace(new Token(TokenType.DOMAIN_COUNT_TERM, t.str.substring(5), t.displayStr));
        } else if (t.str.startsWith("qs=")) {
            entity.replace(new Token(TokenType.QS_TERM, t.str.substring(3), t.displayStr));
        } else if (t.str.contains(":")) {
            entity.replace(new Token(TokenType.ADVICE_TERM, t.str, t.displayStr));
        }
    }

    private static void handleAdvisoryTerms(TransformList<Token>.Entity entity) {
        var t = entity.value();
        if (t.type == TokenType.LPAREN) {
            entity.remove();
        } else if (t.type == TokenType.RPAREN) {
            entity.remove();
        } else if (t.type == TokenType.LITERAL_TERM) {
            entity.replace(new Token(TokenType.ADVICE_TERM, t.str, "(" + t.str + ")"));
        }
    }


}


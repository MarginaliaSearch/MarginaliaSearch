package nu.marginalia.functions.searchquery.query_parser;

import nu.marginalia.functions.searchquery.query_parser.token.QueryToken;
import nu.marginalia.language.WordPatterns;
import nu.marginalia.util.transform_list.TransformList;

import java.util.List;

public class QueryParser {

    private final QueryTokenizer tokenizer = new QueryTokenizer();

    public List<QueryToken> parse(String query) {
        List<QueryToken> basicTokens = tokenizer.tokenizeQuery(query);

        TransformList<QueryToken> list = new TransformList<>(basicTokens);

        list.transformEach(QueryParser::handleQuoteTokens);
        list.transformEach(QueryParser::trimLiterals);
        list.transformEachPair(QueryParser::createNegatedTerms);
        list.transformEachPair(QueryParser::createPriorityTerms);
        list.transformEach(QueryParser::handleSpecialOperations);
        list.scanAndTransform(QueryToken.LParen.class::isInstance, QueryToken.RParen.class::isInstance, QueryParser::handleAdvisoryTerms);
        list.transformEach(QueryParser::normalizeDomainName);

        return list.getBackingList();
    }

    private static void normalizeDomainName(TransformList<QueryToken>.Entity entity) {
        var t = entity.value();

        if (!(t instanceof QueryToken.LiteralTerm))
            return;

        if (t.str().startsWith("site:")) {
            entity.replace(new QueryToken.LiteralTerm(t.str().toLowerCase(), t.displayStr()));
        }

    }

    private static void handleQuoteTokens(TransformList<QueryToken>.Entity entity) {
        var t = entity.value();

        if (!(t instanceof QueryToken.Quot)) {
            return;
        }

        entity.replace(new QueryToken.QuotTerm(
                t.str().replaceAll("\\s+", WordPatterns.WORD_TOKEN_JOINER),
                t.displayStr()));
    }

    private static void trimLiterals(TransformList<QueryToken>.Entity entity) {
        var t = entity.value();

        if (!(t instanceof QueryToken.LiteralTerm lt))
            return;

        String str = lt.str();
        if (str.isBlank())
            return;

        if (str.endsWith(":") || str.endsWith(".")) {
            entity.replace(new QueryToken.LiteralTerm(str.substring(0, str.length() - 1), lt.displayStr()));
        }

    }

    private static void createNegatedTerms(TransformList<QueryToken>.Entity first, TransformList<QueryToken>.Entity second) {
        var t = first.value();
        var tn = second.value();

        if (!(t instanceof QueryToken.Minus))
            return;
        if (!(tn instanceof QueryToken.LiteralTerm) && !(tn instanceof QueryToken.AdviceTerm))
            return;

        first.remove();

        second.replace(new QueryToken.ExcludeTerm(tn.str(), "-" + tn.displayStr()));
    }

    private static void createPriorityTerms(TransformList<QueryToken>.Entity first, TransformList<QueryToken>.Entity second) {
        var t = first.value();
        var tn = second.value();

        if (!(t instanceof QueryToken.QMark))
            return;
        if (!(tn instanceof QueryToken.LiteralTerm) && !(tn instanceof QueryToken.AdviceTerm))
            return;

        var replacement = new QueryToken.PriorityTerm(tn.str(), "?" + tn.displayStr());

        first.remove();
        second.replace(replacement);
    }

    private static void handleSpecialOperations(TransformList<QueryToken>.Entity entity) {
        var t = entity.value();
        if (!(t instanceof QueryToken.LiteralTerm)) {
            return;
        }

        String str = t.str();

        if (str.startsWith("q") && str.matches("q[=><]\\d+")) {
            entity.replace(new QueryToken.QualityTerm(str.substring(1)));
        } else if (str.startsWith("near:")) {
            entity.replace(new QueryToken.NearTerm(str.substring(5)));
        } else if (str.startsWith("year") && str.matches("year[=><]\\d{4}")) {
            entity.replace(new QueryToken.YearTerm(str.substring(4)));
        } else if (str.startsWith("size") && str.matches("size[=><]\\d+")) {
            entity.replace(new QueryToken.SizeTerm(str.substring(4)));
        } else if (str.startsWith("rank") && str.matches("rank[=><]\\d+")) {
            entity.replace(new QueryToken.RankTerm(str.substring(4)));
        } else if (str.startsWith("qs=")) {
            entity.replace(new QueryToken.QsTerm(str.substring(3)));
        } else if (str.contains(":")) {
            entity.replace(new QueryToken.AdviceTerm(str, t.displayStr()));
        }
    }

    private static void handleAdvisoryTerms(TransformList<QueryToken>.Entity entity) {
        var t = entity.value();
        if (t instanceof QueryToken.LParen) {
            entity.remove();
        } else if (t instanceof QueryToken.RParen) {
            entity.remove();
        } else if (t instanceof QueryToken.LiteralTerm) {
            entity.replace(new QueryToken.AdviceTerm(t.str(), "(" + t.displayStr() + ")"));
        }
    }

}


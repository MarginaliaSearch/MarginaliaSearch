package nu.marginalia.query.svc;

import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.query.model.QueryParams;
import nu.marginalia.query_parser.token.Token;
import nu.marginalia.query_parser.token.TokenVisitor;

public class QueryLimitsAccumulator implements TokenVisitor {
    public SpecificationLimit qualityLimit;
    public SpecificationLimit year;
    public SpecificationLimit size;
    public SpecificationLimit rank;
    public SpecificationLimit domainCount;

    public QueryStrategy queryStrategy = QueryStrategy.AUTO;

    public QueryLimitsAccumulator(QueryParams params) {
        qualityLimit = params.quality();
        year = params.year();
        size = params.size();
        rank = params.rank();
        domainCount = params.domainCount();
    }

    private SpecificationLimit parseSpecificationLimit(String str) {
        int startChar = str.charAt(0);

        int val = Integer.parseInt(str.substring(1));
        if (startChar == '=') {
            return SpecificationLimit.equals(val);
        } else if (startChar == '<') {
            return SpecificationLimit.lessThan(val);
        } else if (startChar == '>') {
            return SpecificationLimit.greaterThan(val);
        } else {
            return SpecificationLimit.none();
        }
    }

    private QueryStrategy parseQueryStrategy(String str) {
        return switch (str.toUpperCase()) {
            case "RF_TITLE" -> QueryStrategy.REQUIRE_FIELD_TITLE;
            case "RF_SUBJECT" -> QueryStrategy.REQUIRE_FIELD_SUBJECT;
            case "RF_SITE" -> QueryStrategy.REQUIRE_FIELD_SITE;
            case "RF_URL" -> QueryStrategy.REQUIRE_FIELD_URL;
            case "RF_DOMAIN" -> QueryStrategy.REQUIRE_FIELD_DOMAIN;
            case "SENTENCE" -> QueryStrategy.SENTENCE;
            case "TOPIC" -> QueryStrategy.TOPIC;
            default -> QueryStrategy.AUTO;
        };
    }

    @Override
    public void onYearTerm(Token token) {
        year = parseSpecificationLimit(token.str);
    }

    @Override
    public void onSizeTerm(Token token) {
        size = parseSpecificationLimit(token.str);
    }

    @Override
    public void onRankTerm(Token token) {
        rank = parseSpecificationLimit(token.str);
    }

    @Override
    public void onDomainCountTerm(Token token) {
        domainCount = parseSpecificationLimit(token.str);
    }

    @Override
    public void onQualityTerm(Token token) {
        qualityLimit = parseSpecificationLimit(token.str);
    }

    @Override
    public void onQsTerm(Token token) {
        queryStrategy = parseQueryStrategy(token.str);
    }


    @Override
    public void onLiteralTerm(Token token) {}

    @Override
    public void onQuotTerm(Token token) {}

    @Override
    public void onExcludeTerm(Token token) {}

    @Override
    public void onPriorityTerm(Token token) {}

    @Override
    public void onAdviceTerm(Token token) {}
}

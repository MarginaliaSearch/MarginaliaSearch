package nu.marginalia.functions.searchquery.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.functions.searchquery.query_parser.QueryExpansion;
import nu.marginalia.functions.searchquery.query_parser.token.QueryToken;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.language.WordPatterns;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.ProcessedQuery;
import nu.marginalia.functions.searchquery.query_parser.QueryParser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Singleton
public class QueryFactory {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final QueryParser queryParser = new QueryParser();
    private final QueryExpansion queryExpansion;


    @Inject
    public QueryFactory(QueryExpansion queryExpansion)
    {
        this.queryExpansion = queryExpansion;
    }



    public ProcessedQuery createQuery(QueryParams params) {
        final var query = params.humanQuery();

        if (query.length() > 1000) {
            throw new IllegalArgumentException("Query too long");
        }

        List<String> searchTermsHuman = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        List<QueryToken> basicQuery = queryParser.parse(query);

        if (basicQuery.size() >= 12) {
            problems.add("Your search query is too long");
            basicQuery.clear();
        }

        List<String> searchTermsExclude = new ArrayList<>();
        List<String> searchTermsInclude = new ArrayList<>();
        List<String> searchTermsAdvice = new ArrayList<>();
        List<String> searchTermsPriority = new ArrayList<>();
        List<List<String>> searchTermCoherences = new ArrayList<>();

        SpecificationLimit qualityLimit = SpecificationLimit.none();
        SpecificationLimit year = SpecificationLimit.none();
        SpecificationLimit size = SpecificationLimit.none();
        SpecificationLimit rank = SpecificationLimit.none();
        QueryStrategy queryStrategy = QueryStrategy.AUTO;

        String domain = null;

        for (QueryToken t : basicQuery) {
            switch (t) {
                case QueryToken.QuotTerm(String str, String displayStr) -> {
                    analyzeSearchTerm(problems, str, displayStr);
                    searchTermsHuman.addAll(Arrays.asList(displayStr.replace("\"", "").split("\\s+")));

                    String[] parts = StringUtils.split(str, '_');

                    // Checking for stop words here is a bit of a stop-gap to fix the issue of stop words being
                    // required in the query (which is a problem because they are not indexed). How to do this
                    // in a clean way is a bit of an open problem that may not get resolved until query-parsing is
                    // improved.

                    if (parts.length > 1 && !anyPartIsStopWord(parts)) {
                        // Prefer that the actual n-gram is present
                        searchTermsAdvice.add(str);

                        // Require that the terms appear in the same sentence
                        searchTermCoherences.add(Arrays.asList(parts));

                        // Require that each term exists in the document
                        // (needed for ranking)
                        searchTermsInclude.addAll(Arrays.asList(parts));
                    }
                    else {
                        searchTermsInclude.add(str);
                    }
                }
                case QueryToken.LiteralTerm(String str, String displayStr) -> {
                    analyzeSearchTerm(problems, str, displayStr);
                    searchTermsHuman.addAll(Arrays.asList(displayStr.split("\\s+")));

                    searchTermsInclude.add(str);
                }


                case QueryToken.ExcludeTerm(String str, String displayStr) -> searchTermsExclude.add(str);
                case QueryToken.PriorityTerm(String str, String displayStr) -> searchTermsPriority.add(str);
                case QueryToken.AdviceTerm(String str, String displayStr) -> {
                    searchTermsAdvice.add(str);

                    if (str.toLowerCase().startsWith("site:")) {
                        domain = str.substring("site:".length());
                    }
                }

                case QueryToken.YearTerm(String str) -> year = parseSpecificationLimit(str);
                case QueryToken.SizeTerm(String str) -> size = parseSpecificationLimit(str);
                case QueryToken.RankTerm(String str) -> rank = parseSpecificationLimit(str);
                case QueryToken.QualityTerm(String str) -> qualityLimit = parseSpecificationLimit(str);
                case QueryToken.QsTerm(String str) -> queryStrategy = parseQueryStrategy(str);

                default -> {}
            }
        }

        if (searchTermsInclude.isEmpty() && !searchTermsAdvice.isEmpty()) {
            searchTermsInclude.addAll(searchTermsAdvice);
            searchTermsAdvice.clear();
        }

        List<Integer> domainIds = params.domainIds();

        var limits = params.limits();
        // Disable limits on number of results per domain if we're searching with a site:-type term
        if (domain != null) {
            limits = limits.forSingleDomain();
        }

        var expansion = queryExpansion.expandQuery(searchTermsInclude);
        searchTermCoherences.addAll(expansion.extraCoherences());

        var searchQuery = new SearchQuery(
                expansion.compiledQuery(),
                searchTermsInclude,
                searchTermsExclude,
                searchTermsAdvice,
                searchTermsPriority,
                searchTermCoherences
        );

        var specsBuilder = SearchSpecification.builder()
                .query(searchQuery)
                .humanQuery(query)
                .quality(qualityLimit)
                .year(year)
                .size(size)
                .rank(rank)
                .domains(domainIds)
                .queryLimits(limits)
                .searchSetIdentifier(params.identifier())
                .queryStrategy(queryStrategy);

        SearchSpecification specs = specsBuilder.build();

        specs.query.searchTermsAdvice.addAll(params.tacitAdvice());
        specs.query.searchTermsPriority.addAll(params.tacitPriority());
        specs.query.searchTermsExclude.addAll(params.tacitExcludes());

        return new ProcessedQuery(specs, searchTermsHuman, domain);
    }

    private void analyzeSearchTerm(List<String> problems, String str, String displayStr) {
        final String word = str;

        if (word.length() < WordPatterns.MIN_WORD_LENGTH) {
            problems.add("Search term \"" + displayStr + "\" too short");
        }
        if (!word.contains("_") && word.length() >= WordPatterns.MAX_WORD_LENGTH) {
            problems.add("Search term \"" + displayStr + "\" too long");
        }
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
            case "RF_LINK" -> QueryStrategy.REQUIRE_FIELD_LINK;
            case "SENTENCE" -> QueryStrategy.SENTENCE;
            case "TOPIC" -> QueryStrategy.TOPIC;
            default -> QueryStrategy.AUTO;
        };
    }


    private boolean anyPartIsStopWord(String[] parts) {
        for (String part : parts) {
            if (WordPatterns.isStopWord(part)) {
                return true;
            }
        }
        return false;
    }
}

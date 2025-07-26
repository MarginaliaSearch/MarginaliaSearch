package nu.marginalia.functions.searchquery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcResultRankingParameters;
import nu.marginalia.api.searchquery.model.query.*;
import nu.marginalia.functions.searchquery.query_parser.QueryExpansion;
import nu.marginalia.functions.searchquery.query_parser.QueryParser;
import nu.marginalia.functions.searchquery.query_parser.token.QueryToken;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.language.WordPatterns;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
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

    public ProcessedQuery createQuery(QueryParams params,
                                      @Nullable RpcResultRankingParameters rankingParams) {
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

        SearchQuery.SearchQueryBuilder queryBuilder = SearchQuery.builder();

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

                    // Trim down tokens to match the behavior of the tokenizer used in indexing
                    for (int i = 0; i < parts.length; i++) {
                        String part = parts[i];

                        if (part.endsWith("'s") && part.length() > 2) {
                            part = part.substring(0, part.length()-2);
                        }

                        parts[i] = part;
                    }

                    if (parts.length > 1) {
                        // Require that the terms appear in sequence
                        queryBuilder.phraseConstraint(SearchPhraseConstraint.mandatory(parts));

                        // Construct a regular query from the parts in the quoted string
                        queryBuilder.include(parts);

                        // Prefer that the actual n-gram is present
                        queryBuilder.priority(str);
                    }
                    else {
                        // If the quoted word is a single word, we don't need to do more than include it in the search
                        queryBuilder.include(str);
                    }
                }

                case QueryToken.LiteralTerm(String str, String displayStr) -> {
                    analyzeSearchTerm(problems, str, displayStr);
                    searchTermsHuman.addAll(Arrays.asList(displayStr.split("\\s+")));

                    queryBuilder.include(str);
                }

                case QueryToken.ExcludeTerm(String str, String displayStr) -> queryBuilder.exclude(str);
                case QueryToken.PriorityTerm(String str, String displayStr) -> queryBuilder.priority(str);
                case QueryToken.AdviceTerm(String str, String displayStr) -> {
                    queryBuilder.advice(str);

                    if (str.toLowerCase().startsWith("site:")) {
                        domain = str.substring("site:".length());
                    }
                }

                case QueryToken.YearTerm(SpecificationLimit limit, String displayStr) -> year = limit;
                case QueryToken.SizeTerm(SpecificationLimit limit, String displayStr) -> size = limit;
                case QueryToken.RankTerm(SpecificationLimit limit, String displayStr) -> rank = limit;
                case QueryToken.QualityTerm(SpecificationLimit limit, String displayStr) -> qualityLimit = limit;
                case QueryToken.QsTerm(String str) -> queryStrategy = parseQueryStrategy(str);

                default -> {}
            }
        }

        queryBuilder.promoteNonRankingTerms();

        List<Integer> domainIds = params.domainIds();

        var limits = params.limits();
        // Disable limits on number of results per domain if we're searching with a site:-type term
        if (domain != null) {
            limits = RpcQueryLimits.newBuilder(limits)
                    .setResultsByDomain(limits.getResultsTotal())
                    .build();
        }

        var expansion = queryExpansion.expandQuery(queryBuilder.searchTermsInclude);

        // Query expansion may produce suggestions for phrase constraints,
        // add these to the query
        for (var coh : expansion.optionalPharseConstraints()) {
            queryBuilder.phraseConstraint(SearchPhraseConstraint.optional(coh));
        }

        // add a pseudo-constraint for the full query
        queryBuilder.phraseConstraint(SearchPhraseConstraint.full(expansion.fullPhraseConstraint()));

        queryBuilder.compiledQuery(expansion.compiledQuery());

        var specsBuilder = SearchSpecification.builder()
                .query(queryBuilder.build())
                .quality(qualityLimit)
                .year(year)
                .size(size)
                .rank(rank)
                .rankingParams(rankingParams)
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
}

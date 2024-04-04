package nu.marginalia.functions.searchquery.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.functions.searchquery.query_parser.QueryExpansion;
import nu.marginalia.language.WordPatterns;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.ProcessedQuery;
import nu.marginalia.functions.searchquery.query_parser.QueryParser;
import nu.marginalia.functions.searchquery.query_parser.token.Token;
import nu.marginalia.functions.searchquery.query_parser.token.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

        List<Token> basicQuery = queryParser.parse(query);

        if (basicQuery.size() >= 12) {
            problems.add("Your search query is too long");
            basicQuery.clear();
        }


        QueryLimitsAccumulator qualityLimits = new QueryLimitsAccumulator(params);

        for (Token t : basicQuery) {
            if (t.type == TokenType.QUOT_TERM || t.type == TokenType.LITERAL_TERM) {
                if (t.str.startsWith("site:")) {
                    t.str = normalizeDomainName(t.str);
                }

                searchTermsHuman.addAll(toHumanSearchTerms(t));
                analyzeSearchTerm(problems, t);
            }

            t.visit(qualityLimits);
        }

        QuerySearchTermsAccumulator termsAccumulator = new QuerySearchTermsAccumulator(basicQuery);
        String domain = termsAccumulator.domain;

        List<Integer> domainIds = params.domainIds();

        var limits = params.limits();
        // Disable limits on number of results per domain if we're searching with a site:-type term
        if (domain != null) {
            limits = limits.forSingleDomain();
        }

        var specsBuilder = SearchSpecification.builder()
                .query(
                        new SearchQuery(
                                queryExpansion.expandQuery(
                                        termsAccumulator.searchTermsInclude
                                ),
                                termsAccumulator.searchTermsInclude,
                                termsAccumulator.searchTermsExclude,
                                termsAccumulator.searchTermsAdvice,
                                termsAccumulator.searchTermsPriority,
                                termsAccumulator.searchTermCoherences
                        )
                )
                .humanQuery(query)
                .quality(qualityLimits.qualityLimit)
                .year(qualityLimits.year)
                .size(qualityLimits.size)
                .rank(qualityLimits.rank)
                .domains(domainIds)
                .queryLimits(limits)
                .searchSetIdentifier(params.identifier())
                .rankingParams(ResultRankingParameters.sensibleDefaults())
                .queryStrategy(qualityLimits.queryStrategy);

        SearchSpecification specs = specsBuilder.build();

        specs.query.searchTermsAdvice.addAll(params.tacitAdvice());
        specs.query.searchTermsPriority.addAll(params.tacitPriority());
        specs.query.searchTermsExclude.addAll(params.tacitExcludes());

        return new ProcessedQuery(specs, searchTermsHuman, domain);
    }

    private String normalizeDomainName(String str) {
        return str.toLowerCase();
    }

    private List<String> toHumanSearchTerms(Token t) {
        if (t.type == TokenType.LITERAL_TERM) {
            return Arrays.asList(t.displayStr.split("\\s+"));
        }
        else if (t.type == TokenType.QUOT_TERM) {
            return Arrays.asList(t.displayStr.replace("\"", "").split("\\s+"));

        }
        return Collections.emptyList();
    }

    private void analyzeSearchTerm(List<String> problems, Token term) {
        final String word = term.str;

        if (word.length() < WordPatterns.MIN_WORD_LENGTH) {
            problems.add("Search term \"" + term.displayStr + "\" too short");
        }
        if (!word.contains("_") && word.length() >= WordPatterns.MAX_WORD_LENGTH) {
            problems.add("Search term \"" + term.displayStr + "\" too long");
        }
    }

}

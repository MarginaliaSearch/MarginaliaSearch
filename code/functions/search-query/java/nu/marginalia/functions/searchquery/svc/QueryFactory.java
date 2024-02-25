package nu.marginalia.functions.searchquery.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.LanguageModels;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.util.language.EnglishDictionary;
import nu.marginalia.language.WordPatterns;
import nu.marginalia.util.ngrams.NGramBloomFilter;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.ProcessedQuery;
import nu.marginalia.functions.searchquery.query_parser.QueryParser;
import nu.marginalia.functions.searchquery.query_parser.QueryPermutation;
import nu.marginalia.functions.searchquery.query_parser.QueryVariants;
import nu.marginalia.functions.searchquery.query_parser.token.Token;
import nu.marginalia.functions.searchquery.query_parser.token.TokenType;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Singleton
public class QueryFactory {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final int RETAIN_QUERY_VARIANT_COUNT = 5;
    private final ThreadLocal<QueryVariants> queryVariants;
    private final QueryParser queryParser = new QueryParser();


    @Inject
    public QueryFactory(LanguageModels lm,
                        TermFrequencyDict dict,
                        EnglishDictionary englishDictionary,
                        NGramBloomFilter nGramBloomFilter) {
        this.queryVariants = ThreadLocal.withInitial(() -> new QueryVariants(lm ,dict, nGramBloomFilter, englishDictionary));
    }


    public QueryPermutation getQueryPermutation() {
        return new QueryPermutation(queryVariants.get());
    }

    public ProcessedQuery createQuery(QueryParams params) {
        final var processedQuery =  createQuery(getQueryPermutation(), params);
        final List<SearchSubquery> subqueries = processedQuery.specs.subqueries;

        // There used to be a piece of logic here that would try to figure out which one of these subqueries were the "best",
        // it's gone for the moment, but it would be neat if it resurrected somehow

        trimArray(subqueries, RETAIN_QUERY_VARIANT_COUNT);

        return processedQuery;
    }

    private void trimArray(List<?> arr, int maxSize) {
        if (arr.size() > maxSize) {
            arr.subList(0, arr.size() - maxSize).clear();
        }
    }

    public ProcessedQuery createQuery(QueryPermutation queryPermutation,
                                      QueryParams params)
    {
        final var query = params.humanQuery();

        if (query.length() > 1000) {
            throw new IllegalArgumentException("Query too long");
        }

        List<String> searchTermsHuman = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        String domain = null;

        var basicQuery = queryParser.parse(query);

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

        var queryPermutations = queryPermutation.permuteQueriesNew(basicQuery);
        List<SearchSubquery> subqueries = new ArrayList<>();

        for (var parts : queryPermutations) {
            QuerySearchTermsAccumulator termsAccumulator = new QuerySearchTermsAccumulator(parts);

            SearchSubquery subquery = termsAccumulator.createSubquery();

            domain = termsAccumulator.domain;

            subqueries.add(subquery);
        }

        List<Integer> domainIds = params.domainIds();

        var limits = params.limits();
        // Disable limits on number of results per domain if we're searching with a site:-type term
        if (domain != null) {
            limits = limits.forSingleDomain();
        }

        var specsBuilder = SearchSpecification.builder()
                .subqueries(subqueries)
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

        for (var sq : specs.subqueries) {
            sq.searchTermsAdvice.addAll(params.tacitAdvice());
            sq.searchTermsPriority.addAll(params.tacitPriority());
            sq.searchTermsInclude.addAll(params.tacitIncludes());
            sq.searchTermsExclude.addAll(params.tacitExcludes());
        }

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

package nu.marginalia.search.query;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.LanguageModels;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.client.model.results.ResultRankingParameters;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.language.EnglishDictionary;
import nu.marginalia.ngrams.NGramBloomFilter;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import nu.marginalia.query_parser.QueryParser;
import nu.marginalia.query_parser.QueryPermutation;
import nu.marginalia.query_parser.QueryVariants;
import nu.marginalia.query_parser.token.Token;
import nu.marginalia.query_parser.token.TokenType;
import nu.marginalia.search.db.DbNearDomainsQuery;
import nu.marginalia.search.model.SearchProfile;
import nu.marginalia.search.query.model.SearchQuery;
import nu.marginalia.search.query.model.UserSearchParameters;
import nu.marginalia.language.WordPatterns;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.*;

@Singleton
public class QueryFactory {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DbNearDomainsQuery dbNearDomainsQuery;

    private static final int RETAIN_QUERY_VARIANT_COUNT = 5;
    private final ThreadLocal<QueryVariants> queryVariants;

    private final QueryParser queryParser = new QueryParser();


    @Inject
    public QueryFactory(LanguageModels lm,
                        TermFrequencyDict dict,
                        EnglishDictionary englishDictionary,
                        NGramBloomFilter nGramBloomFilter,
                        DbNearDomainsQuery dbNearDomainsQuery) {
        this.dbNearDomainsQuery = dbNearDomainsQuery;

        this.queryVariants = ThreadLocal.withInitial(() -> new QueryVariants(lm ,dict, nGramBloomFilter, englishDictionary));
    }

    public QueryParser getParser() {
        return new QueryParser();
    }

    public QueryPermutation getQueryPermutation() {
        return new QueryPermutation(queryVariants.get());
    }

    public SearchQuery createQuery(UserSearchParameters params) {
        final var processedQuery =  createQuery(getQueryPermutation(), params);
        final List<SearchSubquery> subqueries = processedQuery.specs.subqueries;

        // There used to be a piece of logic here that would try to figure out which one of these subqueries were the "best",
        // it's gone for the moment, but it would be neat if it resurrected somehow

        trimArray(subqueries, RETAIN_QUERY_VARIANT_COUNT);

        return processedQuery;
    }

    public SearchQuery createQuery(SearchProfile profile,
                                               int limitPerDomain,
                                               int limitTotal,
                                               String... termsInclude)
    {
        List<SearchSubquery> sqs = new ArrayList<>();

        sqs.add(new SearchSubquery(
                Arrays.asList(termsInclude),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()));

        var specs = SearchSpecification.builder()
                .subqueries(sqs)
                .domains(Collections.emptyList())
                .searchSetIdentifier(profile.searchSetIdentifier)
                .queryLimits(new QueryLimits(limitPerDomain, limitTotal, 250, 8192))
                .humanQuery("")
                .year(SpecificationLimit.none())
                .size(SpecificationLimit.none())
                .rank(SpecificationLimit.none())
                .rankingParams(ResultRankingParameters.sensibleDefaults())
                .quality(SpecificationLimit.none())
                .queryStrategy(QueryStrategy.AUTO)
                .build();

        return new SearchQuery(specs);
    }

    private void trimArray(List<?> arr, int maxSize) {
        if (arr.size() > maxSize) {
            arr.subList(0, arr.size() - maxSize).clear();
        }
    }

    public SearchQuery createQuery(QueryPermutation queryPermutation,
                                   UserSearchParameters params)
    {
        final var query = params.humanQuery();
        final var profile = params.profile();

        if (query.length() > 1000) {
            Spark.halt(HttpStatus.BAD_REQUEST_400, "That's too much, man");
        }

        List<String> searchTermsHuman = new ArrayList<>();
        List<String> problems = new ArrayList<>();


        String near = null,
               domain = null;

        var basicQuery = queryParser.parse(query);

        if (basicQuery.size() >= 8) {
            problems.add("Your search query is too long");
            basicQuery.clear();
        }


        QueryLimitsAccumulator qualityLimits = new QueryLimitsAccumulator(profile);

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
            QuerySearchTermsAccumulator termsAccumulator = new QuerySearchTermsAccumulator(profile, parts);

            SearchSubquery subquery = termsAccumulator.createSubquery();

            near = termsAccumulator.near;
            domain = termsAccumulator.domain;

            params.profile().addTacitTerms(subquery);
            params.jsSetting().addTacitTerms(subquery);

            subqueries.add(subquery);
        }

        List<Integer> domains = Collections.emptyList();

        if (near != null) {
            if (domain == null) {
                domains = dbNearDomainsQuery.getRelatedDomains(near, problems::add);
            }
        }

        int domainLimit;
        if (domain != null) {
            domainLimit = 1000;
        } else {
            domainLimit = 2;
        }

        var specsBuilder = SearchSpecification.builder()
                .subqueries(subqueries)
                .queryLimits(new QueryLimits(domainLimit, 100, 250, 4096))
                .humanQuery(query)
                .quality(qualityLimits.qualityLimit)
                .year(qualityLimits.year)
                .size(qualityLimits.size)
                .rank(qualityLimits.rank)
                .domains(domains)
                .rankingParams(ResultRankingParameters.sensibleDefaults())
                .queryStrategy(qualityLimits.queryStrategy)
                .searchSetIdentifier(profile.searchSetIdentifier);

        SearchSpecification specs = specsBuilder.build();

        return new SearchQuery(specs, searchTermsHuman, domain);
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

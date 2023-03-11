package nu.marginalia.search.query;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.LanguageModels;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.language.statistics.EnglishDictionary;
import nu.marginalia.language.statistics.NGramBloomFilter;
import nu.marginalia.language.statistics.TermFrequencyDict;
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
import nu.marginalia.search.valuation.SearchResultValuator;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.*;

@Singleton
public class QueryFactory {

    private final EnglishDictionary englishDictionary;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SearchResultValuator searchResultValuator;
    private final DbNearDomainsQuery dbNearDomainsQuery;

    private static final int RETAIN_QUERY_VARIANT_COUNT = 5;
    private final ThreadLocal<QueryVariants> queryVariants;

    private final QueryParser queryParser = new QueryParser();


    @Inject
    public QueryFactory(LanguageModels lm,
                        TermFrequencyDict dict,
                        EnglishDictionary englishDictionary,
                        NGramBloomFilter nGramBloomFilter,
                        SearchResultValuator searchResultValuator,
                        DbNearDomainsQuery dbNearDomainsQuery) {

        this.englishDictionary = englishDictionary;
        this.searchResultValuator = searchResultValuator;
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

        for (var sq : subqueries) {
            sq.setValue(searchResultValuator.preEvaluate(sq));
        }

        subqueries.sort(Comparator.comparing(SearchSubquery::getValue));
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
                Collections.emptyList()
        ));

        var specs = SearchSpecification.builder()
                .subqueries(sqs)
                .domains(Collections.emptyList())
                .searchSetIdentifier(profile.searchSetIdentifier)
                .queryLimits(new QueryLimits(limitPerDomain, limitTotal, 150, 2048))
                .humanQuery("")
                .year(SpecificationLimit.none())
                .size(SpecificationLimit.none())
                .rank(SpecificationLimit.none())
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
        String domain = null;

        QueryStrategy queryStrategy = QueryStrategy.AUTO;

        var basicQuery = queryParser.parse(query);

        if (basicQuery.size() >= 8) {
            problems.add("Your search query is too long");
            basicQuery.clear();
        }

        SpecificationLimit qualityLimit = profile.getQualityLimit();
        SpecificationLimit year = profile.getYearLimit();
        SpecificationLimit size = profile.getSizeLimit();
        SpecificationLimit rank = SpecificationLimit.none();

        for (Token t : basicQuery) {
            if (t.type == TokenType.QUOT_TERM || t.type == TokenType.LITERAL_TERM) {
                if (t.str.startsWith("site:")) {
                    t.str = normalizeDomainName(t.str);
                }

                searchTermsHuman.addAll(toHumanSearchTerms(t));
                analyzeSearchTerm(problems, t);
            }
            if (t.type == TokenType.QUALITY_TERM) {
                qualityLimit = parseSpecificationLimit(t.str);
            }
            if (t.type == TokenType.YEAR_TERM) {
                year = parseSpecificationLimit(t.str);
            }
            if (t.type == TokenType.SIZE_TERM) {
                size = parseSpecificationLimit(t.str);
            }
            if (t.type == TokenType.RANK_TERM) {
                rank = parseSpecificationLimit(t.str);
            }
            if (t.type == TokenType.QS_TERM) {
                queryStrategy = parseQueryStrategy(t.str);
            }
        }

        var queryPermutations = queryPermutation.permuteQueriesNew(basicQuery);
        List<SearchSubquery> subqueries = new ArrayList<>();

        String near = profile.getNearDomain();


        for (var parts : queryPermutations) {
            List<String> searchTermsExclude = new ArrayList<>();
            List<String> searchTermsInclude = new ArrayList<>();
            List<String> searchTermsAdvice = new ArrayList<>();
            List<String> searchTermsPriority = new ArrayList<>();

            for (Token t : parts) {
                switch (t.type) {
                    case EXCLUDE_TERM:
                        searchTermsExclude.add(t.str);
                        break;
                    case ADVICE_TERM:
                        searchTermsAdvice.add(t.str);
                        if (t.str.toLowerCase().startsWith("site:")) {
                            domain = t.str.substring("site:".length());
                        }
                        break;
                    case PRIORTY_TERM:
                        searchTermsPriority.add(t.str);
                        break;
                    case LITERAL_TERM: // fallthrough;
                    case QUOT_TERM:
                        searchTermsInclude.add(t.str);
                        break;
                    case QUALITY_TERM:
                    case YEAR_TERM:
                    case SIZE_TERM:
                    case RANK_TERM:
                    case QS_TERM:
                        break; //
                    case NEAR_TERM:
                        near = t.str;
                        break;

                    default:
                        logger.warn("Unexpected token type {}", t);
                }
            }

            if (searchTermsInclude.isEmpty() && !searchTermsAdvice.isEmpty()) {
                searchTermsInclude.addAll(searchTermsAdvice);
                searchTermsAdvice.clear();
            }

            SearchSubquery subquery = new SearchSubquery(searchTermsInclude, searchTermsExclude, searchTermsAdvice, searchTermsPriority);

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
            domainLimit = 100;
        } else {
            domainLimit = 2;
        }

        var specsBuilder = SearchSpecification.builder()
                .subqueries(subqueries)
                .queryLimits(new QueryLimits(domainLimit, 100, 250, 4096))
                .humanQuery(query)
                .quality(qualityLimit)
                .year(year)
                .size(size)
                .rank(rank)
                .domains(domains)
                .queryStrategy(queryStrategy)
                .searchSetIdentifier(profile.searchSetIdentifier);

        SearchSpecification specs = specsBuilder.build();

        return new SearchQuery(specs, searchTermsHuman, domain);
    }

    private SpecificationLimit parseSpecificationLimit(String str) {
        int startChar = str.charAt(0);

        int val = Integer.parseInt(str.substring(1));
        if (startChar == '=') {
            return SpecificationLimit.equals(val);
        }
        else if (startChar == '<') {
            return SpecificationLimit.lessThan(val);
        }
        else if (startChar == '>') {
            return SpecificationLimit.greaterThan(val);
        }
        else {
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
        if (!word.contains("_") && !WordPatterns.wordPattern.matcher(word.replaceAll("[_:]","")).matches()) {
            problems.add("The term \"" + term.displayStr + "\" contains characters that are not currently supported");
        }
    }

}

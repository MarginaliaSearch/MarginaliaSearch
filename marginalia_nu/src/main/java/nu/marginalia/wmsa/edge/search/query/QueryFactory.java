package nu.marginalia.wmsa.edge.search.query;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.assistant.dict.NGramBloomFilter;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSpecification;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSubquery;
import nu.marginalia.wmsa.edge.search.model.EdgeSearchProfile;
import nu.marginalia.wmsa.edge.search.query.model.EdgeSearchQuery;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import nu.marginalia.wmsa.edge.search.valuation.SearchResultValuator;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.*;

@Singleton
public class QueryFactory {

    private final LanguageModels lm;
    private final TermFrequencyDict dict;
    private final EnglishDictionary englishDictionary;
    private final NGramBloomFilter nGramBloomFilter;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SearchResultValuator searchResultValuator;
    private NearQueryProcessor nearQueryProcessor;

    private static final int RETAIN_QUERY_VARIANT_COUNT = 5;

    @Inject
    public QueryFactory(LanguageModels lm,
                        TermFrequencyDict dict,
                        EnglishDictionary englishDictionary,
                        NGramBloomFilter nGramBloomFilter,
                        SearchResultValuator searchResultValuator,
                        NearQueryProcessor nearQueryProcessor) {
        this.lm = lm;
        this.dict = dict;

        this.englishDictionary = englishDictionary;
        this.nGramBloomFilter = nGramBloomFilter;
        this.searchResultValuator = searchResultValuator;
        this.nearQueryProcessor = nearQueryProcessor;
    }

    public QueryParser getParser() {
        return new QueryParser(englishDictionary, new QueryVariants(lm ,dict, nGramBloomFilter, englishDictionary));
    }

    public EdgeSearchQuery createQuery(EdgeUserSearchParameters params) {
        final var processedQuery =  createQuery(getParser(), params);

        final var newSubqueries = reevaluateSubqueries(processedQuery, params);

        processedQuery.specs.subqueries.clear();
        processedQuery.specs.subqueries.addAll(newSubqueries);

        return processedQuery;
    }

    private List<EdgeSearchSubquery> reevaluateSubqueries(EdgeSearchQuery processedQuery, EdgeUserSearchParameters params) {
        final var profile = params.profile();

        for (var sq : processedQuery.specs.subqueries) {
            sq.setValue(searchResultValuator.preEvaluate(sq));
        }

        trimExcessiveSubqueries(processedQuery.specs.subqueries);

        List<EdgeSearchSubquery> subqueries =
                new ArrayList<>(processedQuery.specs.subqueries.size() * profile.indexBlocks.size());

        for (var sq : processedQuery.specs.subqueries) {
            for (var block : profile.indexBlocks) {
                subqueries.add(sq.withBlock(block).setValue(sq.getValue() * block.ordinal()));
            }
        }

        subqueries.sort(Comparator.comparing(EdgeSearchSubquery::getValue));

        return subqueries;
    }

    private void trimExcessiveSubqueries(List<EdgeSearchSubquery> subqueries) {

        subqueries.sort(Comparator.comparing(EdgeSearchSubquery::getValue));

        if (subqueries.size() > RETAIN_QUERY_VARIANT_COUNT) {
            subqueries.subList(0, subqueries.size() - RETAIN_QUERY_VARIANT_COUNT).clear();
        }
    }


    public EdgeSearchQuery createQuery(QueryParser queryParser, EdgeUserSearchParameters params) {
        final var query = params.humanQuery();
        final var profile = params.profile();

        if (query.length() > 1000) {
            Spark.halt(HttpStatus.BAD_REQUEST_400, "That's too much, man");
        }

        List<String> searchTermsHuman = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String domain = null;

        var basicQuery = queryParser.parse(query);

        if (basicQuery.size() >= 8) {
            problems.add("Your search query is too long");
            basicQuery.clear();
        }

        Integer qualityLimit = null;
        Integer rankLimit = null;

        for (Token t : basicQuery) {
            if (t.type == TokenType.QUOT_TERM || t.type == TokenType.LITERAL_TERM) {
                if (t.str.startsWith("site:")) {
                    t.str = normalizeDomainName(t.str);
                }

                searchTermsHuman.addAll(toHumanSearchTerms(t));
                analyzeSearchTerm(problems, t);
            }
            if (t.type == TokenType.QUALITY_TERM) {
                qualityLimit = Integer.parseInt(t.str);
            }
            if (t.type == TokenType.RANK_TERM) {
                if (profile == EdgeSearchProfile.CORPO) {
                    problems.add("Rank limit (" + t.displayStr + ") ignored in unranked query");
                } else {
                    rankLimit = Integer.parseInt(t.str);
                }
            }
        }



        var queryPermutations = queryParser.permuteQueriesNew(basicQuery);
        List<EdgeSearchSubquery> subqueries = new ArrayList<>();

        String near = profile.getNearDomain();

        for (var parts : queryPermutations) {
            List<String> searchTermsExclude = new ArrayList<>();
            List<String> searchTermsInclude = new ArrayList<>();
            List<String> searchTermsAdvice = new ArrayList<>();

            for (Token t : parts) {
                switch (t.type) {
                    case EXCLUDE_TERM:
                        searchTermsExclude.add(t.str);
                        break;
                    case ADVICE_TERM:
                        searchTermsAdvice.add(t.str);
                        break;
                    case LITERAL_TERM: // fallthrough;
                    case QUOT_TERM:
                        searchTermsInclude.add(t.str);
                        if (t.str.toLowerCase().startsWith("site:")) {
                            domain = t.str.substring("site:".length());
                        }
                        break;
                    case QUALITY_TERM:
                        break; //
                    case NEAR_TERM:
                        near = t.str;
                        break;
                    default:
                        logger.warn("Unexpected token type {}", t);
                }
            }

            EdgeSearchSubquery subquery = new EdgeSearchSubquery(searchTermsInclude, searchTermsExclude, searchTermsAdvice, IndexBlock.Title);

            params.profile().addTacitTerms(subquery);
            params.jsSetting().addTacitTerms(subquery);

            subqueries.add(subquery);
        }

        List<Integer> domains = Collections.emptyList();

        if (near != null) {
            if (domain == null) {
                domains = nearQueryProcessor.getRelatedDomains(near, problems::add);
            }
        }

        if (qualityLimit != null && domains.isEmpty()) {
            problems.add("Quality limit will be ignored when combined with 'near:'");
        }

        var buckets = domains.isEmpty() ? profile.buckets : EdgeSearchProfile.CORPO.buckets;

        EdgeSearchSpecification.EdgeSearchSpecificationBuilder specsBuilder = EdgeSearchSpecification.builder()
                .subqueries(subqueries)
                .limitTotal(100)
                .humanQuery(query)
                .buckets(buckets)
                .timeoutMs(250)
                .fetchSize(4096)
                .quality(qualityLimit)
                .rank(rankLimit)
                .domains(domains);

        if (domain != null) {
            specsBuilder = specsBuilder.limitByDomain(100);
        } else {
            specsBuilder = specsBuilder.limitByDomain(2);
        }

        EdgeSearchSpecification specs = specsBuilder.build();

        return new EdgeSearchQuery(specs, searchTermsHuman, domain);

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

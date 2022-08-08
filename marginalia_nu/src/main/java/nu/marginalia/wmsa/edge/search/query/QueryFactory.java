package nu.marginalia.wmsa.edge.search.query;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSpecification;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSubquery;
import nu.marginalia.wmsa.edge.search.EdgeSearchProfile;
import nu.marginalia.wmsa.edge.search.query.model.EdgeSearchQuery;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.*;

@Singleton
public class QueryFactory {

    private final LanguageModels lm;
    private final NGramDict dict;
    private final EnglishDictionary englishDictionary;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public QueryFactory(LanguageModels lm, NGramDict dict, EnglishDictionary englishDictionary) {
        this.lm = lm;
        this.dict = dict;

        this.englishDictionary = englishDictionary;
    }

    public QueryParser getParser() {
        return new QueryParser(englishDictionary, new QueryVariants(lm ,dict, englishDictionary));
    }

    public EdgeSearchQuery createQuery(EdgeUserSearchParameters params) {
        final var profile = params.profile();
        final var processedQuery =  createQuery(getParser(), params);

        processedQuery.specs.experimental = EdgeSearchProfile.CORPO.equals(profile);
        processedQuery.specs.stagger = EdgeSearchProfile.YOLO.equals(profile);

        final var newSubqueries = reevaluateSubqueries(processedQuery, params);

        processedQuery.specs.subqueries.clear();
        processedQuery.specs.subqueries.addAll(newSubqueries);

        return processedQuery;
    }

    private List<EdgeSearchSubquery> reevaluateSubqueries(EdgeSearchQuery processedQuery, EdgeUserSearchParameters params) {
        final var profile = params.profile();

        List<EdgeSearchSubquery> subqueries =
                new ArrayList<>(processedQuery.specs.subqueries.size() * profile.indexBlocks.size());

        for (var sq : processedQuery.specs.subqueries) {
            for (var block : profile.indexBlocks) {
                subqueries.add(sq.withBlock(block));
            }
        }

        subqueries.sort(Comparator.comparing(sq -> -sq.termSize()*2.3 + sq.block.sortOrder));

        return subqueries;
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

        for (Token t : basicQuery) {
            if (t.type == TokenType.QUOT_TERM || t.type == TokenType.LITERAL_TERM) {
                if (t.str.startsWith("site:")) {
                    t.str = normalizeDomainName(t.str);
                }

                searchTermsHuman.addAll(toHumanSearchTerms(t));
                analyzeSearchTerm(problems, t);
            }
        }

        var queryPermutations = queryParser.permuteQueriesNew(basicQuery);
        List<EdgeSearchSubquery> subqueries = new ArrayList<>();


        for (var parts : queryPermutations) {
            List<String> searchTermsExclude = new ArrayList<>();
            List<String> searchTermsInclude = new ArrayList<>();

            for (Token t : parts) {
                switch (t.type) {
                    case EXCLUDE_TERM:
                        searchTermsExclude.add(t.str);
                        break;
                    case LITERAL_TERM: // fallthrough;
                    case QUOT_TERM:
                        searchTermsInclude.add(t.str);
                        if (t.str.toLowerCase().startsWith("site:")) {
                            domain = t.str.substring("site:".length());
                        }

                        break;
                    default:
                        logger.warn("Unexpected token type {}", t);
                }
            }

            EdgeSearchSubquery subquery = new EdgeSearchSubquery(searchTermsInclude, searchTermsExclude, IndexBlock.TitleKeywords);

            params.profile().addTacitTerms(subquery);
            params.jsSetting().addTacitTerms(subquery);

            subqueries.add(subquery);
        }


        var specsBuilder = EdgeSearchSpecification.builder()
                .subqueries(subqueries)
                .limitByBucket(50)
                .limitTotal(100)
                .humanQuery(query)
                .buckets(profile.buckets);

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

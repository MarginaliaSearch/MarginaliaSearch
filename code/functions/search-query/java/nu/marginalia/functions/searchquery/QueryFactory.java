package nu.marginalia.functions.searchquery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.CompiledSearchFilterSpec;
import nu.marginalia.api.searchquery.model.query.*;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.functions.searchquery.query_parser.QueryExpansion;
import nu.marginalia.functions.searchquery.query_parser.QueryParser;
import nu.marginalia.functions.searchquery.query_parser.token.QueryToken;
import nu.marginalia.language.WordPatterns;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.model.EdgeDomain;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;

@Singleton
public class QueryFactory {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final QueryParser queryParser = new QueryParser();
    private final QueryExpansion queryExpansion;
    private final DbDomainQueries domainQueries;
    private final LanguageConfiguration languageConfiguration;

    @Inject
    public QueryFactory(QueryExpansion queryExpansion,
                        DbDomainQueries domainQueries,
                        LanguageConfiguration languageConfiguration)
    {
        this.queryExpansion = queryExpansion;
        this.domainQueries = domainQueries;
        this.languageConfiguration = languageConfiguration;
    }


    public ProcessedQuery createQuery(RpcQsQuery request,
                                      CompiledSearchFilterSpec searchFilter,
                                      @Nullable RpcResultRankingParameters rankingParams) {

        LanguageDefinition languageDefinition = languageConfiguration.getLanguage(request.getLangIsoCode());

        final var query = request.getHumanQuery();

        if (query.length() > 1000) {
            throw new IllegalArgumentException("Query too long");
        }

        List<String> searchTermsHuman = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        List<QueryToken> basicQuery = queryParser.parse(languageDefinition, query);

        if (basicQuery.size() >= 12) {
            problems.add("Your search query is too long");
            basicQuery.clear();
        }

        SearchQuery.SearchQueryBuilder queryBuilder = SearchQuery.builder();

        SpecificationLimit qualityLimit = searchFilter.quality();
        SpecificationLimit year = searchFilter.year();
        SpecificationLimit size = searchFilter.size();
        SpecificationLimit rank = searchFilter.rank();
        QueryStrategy queryStrategy = searchFilter.queryStrategy();

        String domain = null;

        IntList domainIds = new IntArrayList(searchFilter.domainsInclude());

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
                            part = part.substring(0, part.length() - 2);
                        }

                        parts[i] = part;
                    }

                    if (parts.length > 1) {
                        // Require that the terms appear in sequence
                        queryBuilder.phraseConstraint(SearchPhraseConstraint.mandatory(parts));

                        // Construct a regular query from the parts in the quoted string
                        queryBuilder.include(parts);

                        // Prefer that the actual n-gram is present
                        queryBuilder.priority(str, 1.0f);
                    } else {
                        // If the quoted word is a single word, we don't need to do more than include it in the search
                        queryBuilder.include(str);
                    }
                }

                case QueryToken.LiteralTerm(String str, String displayStr) -> {
                    analyzeSearchTerm(problems, str, displayStr);
                    searchTermsHuman.addAll(Arrays.asList(displayStr.split("\\s+")));

                    queryBuilder.include(str);
                }

                case QueryToken.ExcludeTerm(String str, _) -> queryBuilder.exclude(str);
                case QueryToken.PriorityTerm(String str, _) -> queryBuilder.priority(str, 1.0f);
                case QueryToken.AdviceTerm(String str, _) when str.startsWith("site:*.") -> {
                    String prefix = "site:*.";
                    domain = str.substring(prefix.length());

                    queryBuilder.advice("site:" + domain);
                }
                case QueryToken.AdviceTerm(String str, _) when str.startsWith("site:") -> {
                    domain = str.substring("site:".length());

                    OptionalInt domainIdMaybe = domainQueries.tryGetDomainId(new EdgeDomain(domain));
                    if (domainIdMaybe.isPresent()) {
                        domainIds = IntList.of(domainIdMaybe.getAsInt());
                    } else {
                        domainIds = IntList.of(-1);
                    }

                    if (basicQuery.size() == 1) {
                        // Ensure we can enumerate documents from a website by adding this dummy term
                        // when this is the only token in the query

                        queryBuilder.advice("site:" + domain);
                    }
                }
                case QueryToken.AdviceTerm(String str, _) -> queryBuilder.advice(str);

                case QueryToken.YearTerm(SpecificationLimit limit, _) -> year = limit;
                case QueryToken.SizeTerm(SpecificationLimit limit, _) -> size = limit;
                case QueryToken.RankTerm(SpecificationLimit limit, _) -> rank = limit;
                case QueryToken.QualityTerm(SpecificationLimit limit, _) -> qualityLimit = limit;
                case QueryToken.QsTerm(String str) -> queryStrategy = parseQueryStrategy(str);

                // No-op for lang term
                case QueryToken.LangTerm(String str, _) -> {}
                default -> {}
            }
        }

        queryBuilder.searchTermsAdvice.addAll(searchFilter.termsRequire());
        queryBuilder.searchTermsPriority.addAll(searchFilter.termsPromote());
        queryBuilder.searchTermsPriorityWeight.addAll(searchFilter.termsPromoteAmounts());
        queryBuilder.searchTermsExclude.addAll(searchFilter.termsExclude());

        queryBuilder.promoteNonRankingTerms();

        var limits = request.getQueryLimits();
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

        if (!"NONE".equals(searchFilter.temporalBias())) {
            if (rankingParams == null) {
                rankingParams = RpcResultRankingParameters.newBuilder(PrototypeRankingParameters.sensibleDefaults())
                        .setTemporalBias(RpcTemporalBias.newBuilder().setBias(RpcTemporalBias.Bias.valueOf(searchFilter.temporalBias()))).build();
            }
            else {
                rankingParams = RpcResultRankingParameters.newBuilder(rankingParams)
                        .setTemporalBias(RpcTemporalBias.newBuilder().setBias(RpcTemporalBias.Bias.valueOf(searchFilter.temporalBias()))).build();
            }
        }

        RpcIndexQuery.Builder indexQueryBuilder = RpcIndexQuery.newBuilder()
                .setHumanQuery(request.getHumanQuery())
                .addAllRequiredDomainIds(domainIds)
                .addAllExcludedDomainIds(searchFilter.domainsExclude())
                .addAllPriorityDomainIds(searchFilter.domainsPromote())
                .addAllPriorityDomainIdsWeights(searchFilter.domainsPromoteAmounts())
                .setSearchSetIdentifier(searchFilter.searchSetIdentifier())
                .setLangIsoCode(request.getLangIsoCode())
                .setNsfwFilterTierValue(request.getNsfwFilterTierValue())
                .setQuality(IndexProtobufCodec.convertSpecLimit(qualityLimit))
                .setYear(IndexProtobufCodec.convertSpecLimit(year))
                .setSize(IndexProtobufCodec.convertSpecLimit(size))
                .setRank(IndexProtobufCodec.convertSpecLimit(rank))
                .setQueryStrategy(queryStrategy.name())
                .setQueryLimits(limits)
                .setQuery(IndexProtobufCodec.convertRpcQuery(queryBuilder.build()));

        if (null != rankingParams)
            indexQueryBuilder.setParameters(rankingParams);


        return new ProcessedQuery(indexQueryBuilder.build(), searchTermsHuman, domain, request.getLangIsoCode());
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

package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.api.searchquery.IndexProtobufCodec;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcResultRankingParameters;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryParser;
import nu.marginalia.api.searchquery.model.compiled.CqDataInt;
import nu.marginalia.api.searchquery.model.query.QueryStrategy;
import nu.marginalia.api.searchquery.model.query.SearchPhraseConstraint;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.index.CombinedIndexReader;
import nu.marginalia.index.reverse.IndexLanguageContext;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.language.keywords.KeywordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static nu.marginalia.api.searchquery.IndexProtobufCodec.convertSpecLimit;

public class SearchContext {
    private static final Logger logger = LoggerFactory.getLogger(SearchContext.class);

    public final IndexSearchBudget budget;

    public final int fetchSize;
    public final int limitByDomain;
    public final int limitTotal;

    private final int docCount;

    public final RpcResultRankingParameters params;
    public final SearchQuery searchQuery;
    public final QueryParams queryParams;

    public final CompiledQuery<String> compiledQuery;
    public final CompiledQueryLong compiledQueryIds;

    /** Bitmask whose position correspond to the positions in the compiled query data
     * which are regular words.
     */
    public final BitSet regularMask;

    /** Bitmask whose position correspond to the positions in the compiled query data
     * which are ngrams.
     */
    public final BitSet ngramsMask;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt fullCounts;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt priorityCounts;

    public final TermIdList termIdsAll;
    public final PhraseConstraintGroupList phraseConstraints;

    public final LongList termIdsAdvice;
    public final LongList termIdsExcludes;
    public final LongList termIdsPriority;

    public final IndexLanguageContext languageContext;

    public final Long2ObjectOpenHashMap<String> termIdToString;
    public final IntList searchSetIds;

    public static SearchContext create(CombinedIndexReader currentIndex,
                                       KeywordHasher keywordHasher,
                                       SearchSpecification specsSet,
                                       SearchSet searchSet) {

        var queryParams = new QueryParams(specsSet.quality, specsSet.year, specsSet.size, specsSet.rank, searchSet, specsSet.queryStrategy);
        var rankingParams = specsSet.rankingParams;
        var limits = specsSet.queryLimits;

        return new SearchContext(
                keywordHasher,
                "en", // FIXME: This path currently only supports english
                currentIndex,
                specsSet.query.compiledQuery,
                queryParams,
                specsSet.query,
                rankingParams,
                limits);
    }

    public static SearchContext create(CombinedIndexReader currentIndex,
                                       KeywordHasher keywordHasher,
                                       RpcIndexQuery request, SearchSet searchSet) {
        var limits = request.getQueryLimits();
        var query = IndexProtobufCodec.convertRpcQuery(request.getQuery());

        var queryParams = new QueryParams(
                convertSpecLimit(request.getQuality()),
                convertSpecLimit(request.getYear()),
                convertSpecLimit(request.getSize()),
                convertSpecLimit(request.getRank()),
                searchSet,
                QueryStrategy.valueOf(request.getQueryStrategy()));

        var rankingParams = request.hasParameters() ? request.getParameters() : PrototypeRankingParameters.sensibleDefaults();

        return new SearchContext(
                keywordHasher,
                request.getLangIsoCode(),
                currentIndex,
                query.compiledQuery,
                queryParams,
                query,
                rankingParams,
                limits);
    }

    public SearchContext(
                         KeywordHasher keywordHasher,
                         String langIsoCode,
                         CombinedIndexReader currentIndex,
                         String queryExpression,
                         QueryParams queryParams,
                         SearchQuery query,
                         RpcResultRankingParameters rankingParams,
                         RpcQueryLimits limits)
    {
        this.docCount = currentIndex.totalDocCount();
        this.languageContext = currentIndex.createLanguageContext(langIsoCode);

        this.budget = new IndexSearchBudget(Math.max(limits.getTimeoutMs()/2, limits.getTimeoutMs()-50));
        this.searchQuery = query;
        this.params = rankingParams;
        this.queryParams = queryParams;
        this.searchSetIds = queryParams.searchSet().domainIds();

        this.fetchSize = limits.getFetchSize();
        this.limitByDomain = limits.getResultsByDomain();
        this.limitTotal = limits.getResultsTotal();


        this.compiledQuery = CompiledQueryParser.parse(queryExpression);
        this.compiledQueryIds = compiledQuery.mapToLong(keywordHasher::hashKeyword);
        int[] full = new int[compiledQueryIds.size()];
        int[] prio = new int[compiledQueryIds.size()];

        this.ngramsMask = new BitSet(compiledQuery.size());
        this.regularMask = new BitSet(compiledQuery.size());

        for (int idx = 0; idx < compiledQueryIds.size(); idx++) {
            long id = compiledQueryIds.at(idx);
            full[idx] = currentIndex.numHits(this.languageContext, id);
            prio[idx] = currentIndex.numHitsPrio(this.languageContext, id);

            if (compiledQuery.at(idx).contains("_")) {
                ngramsMask.set(idx);
            }
            else {
                regularMask.set(idx);
            }
        }

        this.fullCounts = new CqDataInt(full);
        this.priorityCounts = new CqDataInt(prio);

        this.termIdsExcludes = new LongArrayList();
        this.termIdsPriority = new LongArrayList();
        this.termIdsAdvice = new LongArrayList();

        for (var word : searchQuery.searchTermsAdvice) {
            termIdsAdvice.add(keywordHasher.hashKeyword(word));
        }

        for (var word : searchQuery.searchTermsExclude) {
            termIdsExcludes.add(keywordHasher.hashKeyword(word));
        }

        for (var word : searchQuery.searchTermsPriority) {
            termIdsPriority.add(keywordHasher.hashKeyword(word));
        }

        LongArrayList termIdsList = new LongArrayList();
        termIdToString = new Long2ObjectOpenHashMap<>();

        for (String term : compiledQuery) {
            long id = keywordHasher.hashKeyword(term);
            termIdsList.add(id);
            termIdToString.put(id, term);
        }

        for (var term : searchQuery.searchTermsPriority) {
            long id = keywordHasher.hashKeyword(term);
            if (termIdToString.containsKey(id))
                continue;
            termIdsList.add(id);
            termIdToString.put(id, term);
        }

        for (var term : searchQuery.searchTermsAdvice) {
            long id = keywordHasher.hashKeyword(term);
            if (termIdToString.containsKey(id))
                continue;
            termIdToString.put(id, term);
        }

        termIdsAll = new TermIdList(termIdsList);

        var constraintsMandatory = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();
        var constraintsFull = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();
        var constraintsOptional = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();

        for (var constraint : searchQuery.phraseConstraints) {
            switch (constraint) {
                case SearchPhraseConstraint.Mandatory(List<String> terms) ->
                        constraintsMandatory.add(new PhraseConstraintGroupList.PhraseConstraintGroup(keywordHasher, terms, termIdsAll));
                case SearchPhraseConstraint.Optional(List<String> terms) ->
                        constraintsOptional.add(new PhraseConstraintGroupList.PhraseConstraintGroup(keywordHasher, terms, termIdsAll));
                case SearchPhraseConstraint.Full(List<String> terms) ->
                        constraintsFull.add(new PhraseConstraintGroupList.PhraseConstraintGroup(keywordHasher, terms, termIdsAll));
            }
        }

        if (constraintsFull.isEmpty()) {
            logger.warn("No full constraints in query, adding empty group");
            constraintsFull.add(new PhraseConstraintGroupList.PhraseConstraintGroup(keywordHasher, List.of(), termIdsAll));
        }

        this.phraseConstraints = new PhraseConstraintGroupList(constraintsFull.getFirst(), constraintsMandatory, constraintsOptional);
    }

    public int termFreqDocCount() {
        return docCount;
    }

    public long[] sortedDistinctIncludes(LongComparator comparator) {
        LongList list = new LongArrayList(compiledQueryIds.copyData());
        list.sort(comparator);
        return list.toLongArray();
    }

}

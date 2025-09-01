package nu.marginalia.index.model;

import gnu.trove.map.hash.TObjectLongHashMap;
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
import nu.marginalia.api.searchquery.model.query.SearchPhraseConstraint;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.index.index.CombinedIndexReader;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.results.model.PhraseConstraintGroupList;
import nu.marginalia.index.results.model.ids.TermIdList;
import nu.marginalia.index.searchset.SearchSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static nu.marginalia.api.searchquery.IndexProtobufCodec.convertSpecLimit;
import static nu.marginalia.index.model.SearchTermsUtil.getWordId;

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

    /** Bitmask whose positiosn correspond to the positions in the compiled query data
     * which are regular words.
     */
    public final BitSet regularMask;

    /** Bitmask whose positiosn correspond to the positions in the compiled query data
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

    public static SearchContext create(CombinedIndexReader currentIndex,
                                       SearchSpecification specsSet, SearchSet searchSet) {

        var compiledQuery = CompiledQueryParser.parse(specsSet.query.compiledQuery);
        var compiledQueryIds = compiledQuery.mapToLong(SearchTermsUtil::getWordId);

        var queryParams = new QueryParams(specsSet.quality, specsSet.year, specsSet.size, specsSet.rank, searchSet, specsSet.queryStrategy);
        var rankingParams = specsSet.rankingParams;
        var limits = specsSet.queryLimits;

        int[] full = new int[compiledQueryIds.size()];
        int[] prio = new int[compiledQueryIds.size()];

        BitSet ngramsMask = new BitSet(compiledQuery.size());
        BitSet regularMask = new BitSet(compiledQuery.size());

        for (int idx = 0; idx < compiledQueryIds.size(); idx++) {
            long id = compiledQueryIds.at(idx);
            full[idx] = currentIndex.numHits(id);
            prio[idx] = currentIndex.numHitsPrio(id);

            if (compiledQuery.at(idx).contains("_")) {
                ngramsMask.set(idx);
            }
            else {
                regularMask.set(idx);
            }
        }

        return new SearchContext(
                queryParams,
                specsSet.query,
                rankingParams,
                limits,
                currentIndex.totalDocCount(),
                compiledQuery,
                compiledQueryIds,
                ngramsMask,
                regularMask,
                new CqDataInt(full),
                new CqDataInt(prio));
    }

    public static SearchContext create(CombinedIndexReader currentIndex, RpcIndexQuery request, SearchSet searchSet) {
        var limits = request.getQueryLimits();
        var query = IndexProtobufCodec.convertRpcQuery(request.getQuery());

        var queryParams = new QueryParams(
                convertSpecLimit(request.getQuality()),
                convertSpecLimit(request.getYear()),
                convertSpecLimit(request.getSize()),
                convertSpecLimit(request.getRank()),
                searchSet,
                QueryStrategy.valueOf(request.getQueryStrategy()));

        var compiledQuery = CompiledQueryParser.parse(query.compiledQuery);
        var compiledQueryIds = compiledQuery.mapToLong(SearchTermsUtil::getWordId);

        var rankingParams = request.hasParameters() ? request.getParameters() : PrototypeRankingParameters.sensibleDefaults();

        int[] full = new int[compiledQueryIds.size()];
        int[] prio = new int[compiledQueryIds.size()];

        BitSet ngramsMask = new BitSet(compiledQuery.size());
        BitSet regularMask = new BitSet(compiledQuery.size());

        for (int idx = 0; idx < compiledQueryIds.size(); idx++) {
            long id = compiledQueryIds.at(idx);
            full[idx] = currentIndex.numHits(id);
            prio[idx] = currentIndex.numHitsPrio(id);

            if (compiledQuery.at(idx).contains("_")) {
                ngramsMask.set(idx);
            }
            else {
                regularMask.set(idx);
            }
        }

        return new SearchContext(
                queryParams,
                query,
                rankingParams,
                limits,
                currentIndex.totalDocCount(),
                compiledQuery,
                compiledQueryIds,
                ngramsMask,
                regularMask,
                new CqDataInt(full),
                new CqDataInt(prio));
    }

    public SearchContext(QueryParams queryParams,
                         SearchQuery query,
                         RpcResultRankingParameters rankingParams,
                         RpcQueryLimits limits,
                         int docCount,
                         CompiledQuery<String> compiledQuery,
                         CompiledQueryLong compiledQueryIds,
                         BitSet ngramsMask,
                         BitSet regularMask,
                         CqDataInt fullCounts,
                         CqDataInt prioCounts)
    {
        this.docCount = docCount;

        this.budget = new IndexSearchBudget(Math.max(limits.getTimeoutMs()/2, limits.getTimeoutMs()-50));
        this.searchQuery = query;
        this.params = rankingParams;
        this.queryParams = queryParams;

        this.fetchSize = limits.getFetchSize();
        this.limitByDomain = limits.getResultsByDomain();
        this.limitTotal = limits.getResultsTotal();

        this.compiledQuery = compiledQuery;
        this.compiledQueryIds = compiledQueryIds;

        this.ngramsMask = ngramsMask;
        this.regularMask = regularMask;

        this.fullCounts = fullCounts;
        this.priorityCounts = prioCounts;

        this.termIdsExcludes = new LongArrayList();
        this.termIdsPriority = new LongArrayList();
        this.termIdsAdvice = new LongArrayList();

        for (var word : searchQuery.searchTermsAdvice) {
            termIdsAdvice.add(getWordId(word));
        }

        for (var word : searchQuery.searchTermsExclude) {
            termIdsExcludes.add(getWordId(word));
        }

        for (var word : searchQuery.searchTermsPriority) {
            termIdsPriority.add(getWordId(word));
        }

        LongArrayList termIdsList = new LongArrayList();
        TObjectLongHashMap<Object> termToId = new TObjectLongHashMap<>();

        for (String word : compiledQuery) {
            long id = SearchTermsUtil.getWordId(word);
            termIdsList.add(id);
            termToId.put(word, id);
        }

        for (var term : searchQuery.searchTermsPriority) {
            if (termToId.containsKey(term)) {
                continue;
            }

            long id = SearchTermsUtil.getWordId(term);
            termIdsList.add(id);
            termToId.put(term, id);
        }

        termIdsAll = new TermIdList(termIdsList);

        var constraintsMandatory = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();
        var constraintsFull = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();
        var constraintsOptional = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();

        for (var constraint : searchQuery.phraseConstraints) {
            switch (constraint) {
                case SearchPhraseConstraint.Mandatory(List<String> terms) ->
                        constraintsMandatory.add(new PhraseConstraintGroupList.PhraseConstraintGroup(terms, termIdsAll));
                case SearchPhraseConstraint.Optional(List<String> terms) ->
                        constraintsOptional.add(new PhraseConstraintGroupList.PhraseConstraintGroup(terms, termIdsAll));
                case SearchPhraseConstraint.Full(List<String> terms) ->
                        constraintsFull.add(new PhraseConstraintGroupList.PhraseConstraintGroup(terms, termIdsAll));
            }
        }

        if (constraintsFull.isEmpty()) {
            logger.warn("No full constraints in query, adding empty group");
            constraintsFull.add(new PhraseConstraintGroupList.PhraseConstraintGroup(List.of(), termIdsAll));
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

    @Override
    public String toString() {
        return "ResultRankingContext{" +
                "docCount=" + docCount +
                ", params=" + params +
                ", regularMask=" + regularMask +
                ", ngramsMask=" + ngramsMask +
                ", fullCounts=" + fullCounts +
                ", priorityCounts=" + priorityCounts +
                '}';
    }
}

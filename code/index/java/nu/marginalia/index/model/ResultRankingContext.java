package nu.marginalia.index.model;

import nu.marginalia.api.searchquery.RpcResultRankingParameters;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.CqDataInt;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.index.index.CombinedIndexReader;

import java.util.BitSet;

public class ResultRankingContext {
    private final int docCount;
    public final RpcResultRankingParameters params;
    public final SearchQuery searchQuery;
    public final QueryParams queryParams;

    public final CompiledQuery<String> compiledQuery;
    public final CompiledQueryLong compiledQueryIds;

    public final BitSet regularMask;
    public final BitSet ngramsMask;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt fullCounts;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt priorityCounts;

    public static ResultRankingContext create(CombinedIndexReader currentIndex, SearchParameters searchParameters) {

        var compiledQueryIds = searchParameters.compiledQueryIds;
        var compiledQuery = searchParameters.compiledQuery;

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

        return new ResultRankingContext(currentIndex.totalDocCount(),
                searchParameters,
                compiledQuery,
                compiledQueryIds,
                ngramsMask,
                regularMask,
                new CqDataInt(full),
                new CqDataInt(prio));
    }

    public ResultRankingContext(int docCount,
                                SearchParameters searchParameters,
                                CompiledQuery<String> compiledQuery,
                                CompiledQueryLong compiledQueryIds,
                                BitSet ngramsMask,
                                BitSet regularMask,
                                CqDataInt fullCounts,
                                CqDataInt prioCounts)
    {
        this.docCount = docCount;

        this.searchQuery = searchParameters.query;
        this.params = searchParameters.rankingParams;
        this.queryParams = searchParameters.queryParams;

        this.compiledQuery = compiledQuery;
        this.compiledQueryIds = compiledQueryIds;

        this.ngramsMask = ngramsMask;
        this.regularMask = regularMask;

        this.fullCounts = fullCounts;
        this.priorityCounts = prioCounts;
    }

    public int termFreqDocCount() {
        return docCount;
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

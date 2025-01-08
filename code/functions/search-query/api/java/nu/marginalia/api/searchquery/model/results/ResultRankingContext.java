package nu.marginalia.api.searchquery.model.results;

import nu.marginalia.api.searchquery.RpcResultRankingParameters;
import nu.marginalia.api.searchquery.model.compiled.CqDataInt;

import java.util.BitSet;

public class ResultRankingContext {
    private final int docCount;
    public final RpcResultRankingParameters params;


    public final BitSet regularMask;
    public final BitSet ngramsMask;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt fullCounts;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt priorityCounts;

    public ResultRankingContext(int docCount,
                                RpcResultRankingParameters params,
                                BitSet ngramsMask,
                                BitSet regularMask,
                                CqDataInt fullCounts,
                                CqDataInt prioCounts)
    {
        this.docCount = docCount;
        this.params = params;

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

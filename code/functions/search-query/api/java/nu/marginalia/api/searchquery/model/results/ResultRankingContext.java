package nu.marginalia.api.searchquery.model.results;

import lombok.ToString;
import nu.marginalia.api.searchquery.model.compiled.CqDataInt;

@ToString
public class ResultRankingContext {
    private final int docCount;
    public final ResultRankingParameters params;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt fullCounts;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt priorityCounts;

    public ResultRankingContext(int docCount,
                                ResultRankingParameters params,
                                CqDataInt fullCounts,
                                CqDataInt prioCounts)
    {
        this.docCount = docCount;
        this.params = params;
        this.fullCounts = fullCounts;
        this.priorityCounts = prioCounts;
    }

    public int termFreqDocCount() {
        return docCount;
    }

}

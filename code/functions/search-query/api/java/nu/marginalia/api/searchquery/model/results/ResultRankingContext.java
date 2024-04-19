package nu.marginalia.api.searchquery.model.results;

import lombok.ToString;
import nu.marginalia.api.searchquery.model.compiled.CqDataInt;

import java.util.BitSet;

@ToString
public class ResultRankingContext {
    private final int docCount;
    public final ResultRankingParameters params;


    public final BitSet regularMask;
    public final BitSet ngramsMask;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt fullCounts;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt priorityCounts;

    public ResultRankingContext(int docCount,
                                ResultRankingParameters params,
                                BitSet ngramsMask,
                                CqDataInt fullCounts,
                                CqDataInt prioCounts)
    {
        this.docCount = docCount;
        this.params = params;

        this.ngramsMask = ngramsMask;

        this.regularMask = new BitSet(ngramsMask.length());
        for (int i = 0; i < ngramsMask.length(); i++) {
            if (!ngramsMask.get(i)) {
                regularMask.set(i);
            }
        }

        this.fullCounts = fullCounts;
        this.priorityCounts = prioCounts;
    }

    public int termFreqDocCount() {
        return docCount;
    }

}

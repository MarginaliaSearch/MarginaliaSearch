package nu.marginalia.index.model;

import java.util.BitSet;

public class PositionDataOffsets {
    private final long[] data;
    public final int nTerms;
    public final int nDocs;

    public PositionDataOffsets(CombinedDocIdList docIds,
                               long[] termIds) {
        nTerms = termIds.length;
        nDocs = docIds.size();

        data = new long[termIds.length * docIds.size()];
    }

    public PositionDataOffsets(CombinedDocIdList docIds,
                               BitSet viableDocuments,
                               long[] termIds,
                               long[][] valuesForTerm)
    {
        nTerms = termIds.length;
        nDocs = docIds.size();

        data = new long[termIds.length * docIds.size()];

        for (int i = 0; i < termIds.length; i++) {
            // Add to the big array of term data offsets
            long[] values = valuesForTerm[i];
            if (null == values)
                continue;

            for (int di = 0; di < nDocs; di++) {
                if (!viableDocuments.get(di))
                    continue;

                // We can omit the position data retrieval if the position mask is zero
                // (likely a synthetic keyword, n-gram, etc.)
                long positionMask = values[nDocs + di] & ~0xFFL;
                if (positionMask == 0)
                    continue;

                data[i + di * nTerms] = values[di];
            }
        }
    }

    public long[] offsetsForDoc(int docIdx, long[] buffer) {
        if (null == buffer)
            buffer = new long[nTerms];

        for (int i = 0; i < nTerms; i++) {
            buffer[i] = data[i + docIdx * nTerms];
        }

        return buffer;
    }

}

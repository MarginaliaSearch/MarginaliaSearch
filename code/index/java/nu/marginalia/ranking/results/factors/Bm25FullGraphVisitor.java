package nu.marginalia.ranking.results.factors;

import nu.marginalia.api.searchquery.model.compiled.CqDataInt;
import nu.marginalia.api.searchquery.model.compiled.CqDataLong;
import nu.marginalia.api.searchquery.model.compiled.CqExpression;
import nu.marginalia.api.searchquery.model.results.Bm25Parameters;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.model.idx.WordMetadata;

import java.util.BitSet;
import java.util.List;

public class Bm25FullGraphVisitor implements CqExpression.DoubleVisitor {
    private static final long AVG_LENGTH = 5000;

    private final CqDataInt counts;
    private final CqDataInt frequencies;
    private final Bm25Parameters bm25Parameters;

    private final int docCount;
    private final int length;

    private final BitSet mask;

    public Bm25FullGraphVisitor(Bm25Parameters bm25Parameters,
                                CqDataInt counts,
                                int length,
                                ResultRankingContext ctx) {
        this.length = length;
        this.bm25Parameters = bm25Parameters;
        this.docCount = ctx.termFreqDocCount();
        this.counts = counts;
        this.frequencies = ctx.fullCounts;
        this.mask = ctx.regularMask;
    }

    @Override
    public double onAnd(List<? extends CqExpression> parts) {
        double value = 0;
        for (var part : parts) {
            value += part.visit(this);
        }
        return value;
    }

    @Override
    public double onOr(List<? extends CqExpression> parts) {
        double value = 0;
        for (var part : parts) {
            value = Math.max(value, part.visit(this));
        }
        return value;
    }

    @Override
    public double onLeaf(int idx) {
        if (!mask.get(idx)) {
            return 0;
        }

        double count = counts.get(idx);

        int freq = frequencies.get(idx);

        return invFreq(docCount, freq) * f(bm25Parameters.k(), bm25Parameters.b(), count, length);
    }

    /**
     *
     * @param docCount Number of documents
     * @param freq Number of matching documents
     */
    private double invFreq(int docCount, int freq) {
        return Math.log(1.0 + (docCount - freq + 0.5) / (freq + 0.5));
    }

    /**
     *
     * @param k  determines the size of the impact of a single term
     * @param b  determines the magnitude of the length normalization
     * @param count   number of occurrences in the document
     * @param length  document length
     */
    private double f(double k, double b, double count, int length) {
        final double lengthRatio = (double) length / AVG_LENGTH;

        return (count * (k + 1)) / (count + k * (1 - b + b * lengthRatio));
    }
}

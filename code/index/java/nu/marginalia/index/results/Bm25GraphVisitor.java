package nu.marginalia.index.results;

import nu.marginalia.api.searchquery.model.compiled.CqDataInt;
import nu.marginalia.api.searchquery.model.compiled.CqExpression;
import nu.marginalia.api.searchquery.model.results.Bm25Parameters;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;

import java.util.BitSet;
import java.util.List;

/** Visitor for calculating the best BM25 score for a graph representing a search query
 */
public class Bm25GraphVisitor implements CqExpression.DoubleVisitor {
    private static final long AVG_LENGTH = 5000;

    private final CqDataInt counts;
    private final CqDataInt frequencies;

    private final double k1;
    private final double b;

    private final int docCount;
    private final int length;

    private final BitSet mask;

    public Bm25GraphVisitor(Bm25Parameters bm25Parameters,
                            CqDataInt counts,
                            int length,
                            ResultRankingContext ctx) {
        this.length = length;

        this.k1 = bm25Parameters.k();
        this.b = bm25Parameters.b();

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

        return invFreq(docCount, freq) * f(count, length);
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
     * @param count   number of occurrences in the document
     * @param length  document length
     */
    private double f(double count, int length) {
        final double lengthRatio = (double) length / AVG_LENGTH;

        return (count * (k1 + 1)) / (count + k1 * (1 - b + b * lengthRatio));
    }
}

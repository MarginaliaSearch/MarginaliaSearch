package nu.marginalia.index.results;

import nu.marginalia.api.searchquery.model.compiled.CqDataInt;
import nu.marginalia.api.searchquery.model.compiled.CqDataLong;
import nu.marginalia.api.searchquery.model.compiled.CqExpression;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.model.idx.WordFlags;

import java.util.List;

public class TermFlagsGraphVisitor implements CqExpression.DoubleVisitor {
    private static final long AVG_LENGTH = 5000;

    private final CqDataLong wordMetaData;
    private final CqDataInt frequencies;
    private final float[] counts;
    private final double k1;
    private final int docCount;

    public TermFlagsGraphVisitor(double k1,
                                 CqDataLong wordMetaData,
                                 float[] counts,
                                 SearchContext ctx) {
        this.k1 = k1;
        this.counts = counts;
        this.docCount = ctx.termFreqDocCount();
        this.wordMetaData = wordMetaData;
        this.frequencies = ctx.fullCounts;
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
        double count = evaluatePriorityScore(idx);

        int freq = frequencies.get(idx);

        // note we override b to zero for priority terms as they are independent of document length
        return invFreq(docCount, freq) * f(k1, 0, count, 0);
    }

    private double evaluatePriorityScore(int idx) {
        byte wordMeta = (byte) wordMetaData.get(idx);
        float pcount = counts[idx];

        double qcount = 0.;

        if ((wordMeta & WordFlags.ExternalLink.asBit()) != 0) {

            qcount += 2.5;

            if ((wordMeta & WordFlags.UrlDomain.asBit()) != 0)
                qcount += 2.5;
            else if ((wordMeta & WordFlags.UrlPath.asBit()) != 0)
                qcount += 1.5;

            if ((wordMeta & WordFlags.Site.asBit()) != 0)
                qcount += 1.25;
            if ((wordMeta & WordFlags.SiteAdjacent.asBit()) != 0)
                qcount += 1.25;
        }
        else {
            if ((wordMeta & WordFlags.UrlDomain.asBit()) != 0)
                qcount += 3;
            else if ((wordMeta & WordFlags.UrlPath.asBit()) != 0)
                qcount += 1;

            if ((wordMeta & WordFlags.Site.asBit()) != 0)
                qcount += 0.5;
            if ((wordMeta & WordFlags.SiteAdjacent.asBit()) != 0)
                qcount += 0.5;
        }

        if ((wordMeta & WordFlags.Title.asBit()) != 0)
            qcount += 1.5;

        if (pcount > 2) {
            if ((wordMeta & WordFlags.Subjects.asBit()) != 0)
                qcount += 1.25;
            if ((wordMeta & WordFlags.NamesWords.asBit()) != 0)
                qcount += 0.25;
        }

        return qcount;
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
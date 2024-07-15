package nu.marginalia.api.searchquery.model.results;

import lombok.*;

@Builder
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Getter // getter for the mustache template engine's behalf
public class ResultRankingParameters {

    /** Tuning for BM25 when applied to full document matches */
    public final Bm25Parameters bm25Params;

    /** Documents below this length are penalized */
    public int shortDocumentThreshold;

    public double shortDocumentPenalty;


    /** Scaling factor associated with domain rank (unscaled rank value is 0-255; high is good) */
    public double domainRankBonus;

    /** Scaling factor associated with document quality (unscaled rank value is 0-15; high is bad) */
    public double qualityPenalty;

    /** Average sentence length values below this threshold are penalized, range [0-4), 2 or 3 is probably what you want */
    public int shortSentenceThreshold;

    /** Magnitude of penalty for documents with low average sentence length */
    public double shortSentencePenalty;

    public double bm25Weight;
    public double tcfFirstPosition;
    public double tcfAvgDist;

    public TemporalBias temporalBias;
    public double temporalBiasWeight;

    public boolean exportDebugData;

    public static ResultRankingParameters sensibleDefaults() {
        return builder()
                .bm25Params(new Bm25Parameters(1.2, 0.5))
                .shortDocumentThreshold(2000)
                .shortDocumentPenalty(2.)
                .domainRankBonus(1/25.)
                .qualityPenalty(1/15.)
                .shortSentenceThreshold(2)
                .shortSentencePenalty(5)
                .bm25Weight(1.)
                .tcfAvgDist(25.)
                .tcfFirstPosition(1) // FIXME: what's a good default?
                .temporalBias(TemporalBias.NONE)
                .temporalBiasWeight(1. / (5.))
                .exportDebugData(false)
                .build();
    }

    public enum TemporalBias {
        RECENT, OLD, NONE
    }
}

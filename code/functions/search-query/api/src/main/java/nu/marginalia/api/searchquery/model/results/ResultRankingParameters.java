package nu.marginalia.api.searchquery.model.results;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Builder @AllArgsConstructor @ToString @EqualsAndHashCode
public class ResultRankingParameters {

    /** Tuning for BM25 when applied to full document matches */
    public final Bm25Parameters fullParams;
    /** Tuning for BM25 when applied to priority matches, terms with relevance signal indicators */
    public final Bm25Parameters prioParams;

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

    public double bm25FullWeight;
    public double bm25PrioWeight;
    public double tcfWeight;

    public TemporalBias temporalBias;
    public double temporalBiasWeight;

    public static ResultRankingParameters sensibleDefaults() {
        return builder()
                .fullParams(new Bm25Parameters(1.2, 0.5))
                .prioParams(new Bm25Parameters(1.5, 0))
                .shortDocumentThreshold(2000)
                .shortDocumentPenalty(2.)
                .domainRankBonus(1/25.)
                .qualityPenalty(1/15.)
                .shortSentenceThreshold(2)
                .shortSentencePenalty(5)
                .bm25FullWeight(1.)
                .bm25PrioWeight(1.)
                .tcfWeight(2.)
                .temporalBias(TemporalBias.NONE)
                .temporalBiasWeight(1. / (5.))
                .build();
    }

    public enum TemporalBias {
        RECENT, OLD, NONE
    }
}

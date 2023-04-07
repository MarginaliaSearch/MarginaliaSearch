package nu.marginalia.index.client.model.results;

import lombok.AllArgsConstructor;
import lombok.Builder;

@Builder @AllArgsConstructor
public class ResultRankingParameters {

    public final Bm25Parameters fullParams;
    public final Bm25Parameters prioParams;
    public int shortDocumentThreshold;
    public double shortDocumentPenalty;
    public double domainRankBonus;
    public double qualityPenalty;
    public int shortSentenceThreshold;
    public double shortSentencePenalty;

    public double bm25FullWeight;
    public double bm25PrioWeight;
    public double tcfWeight;

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
                .build();
    }
}

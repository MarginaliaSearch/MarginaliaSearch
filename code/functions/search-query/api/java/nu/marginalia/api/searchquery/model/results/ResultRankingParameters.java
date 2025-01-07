package nu.marginalia.api.searchquery.model.results;

import java.util.Objects;

public class ResultRankingParameters {

    /**
     * Tuning for BM25 when applied to full document matches
     */
    public final Bm25Parameters bm25Params;

    /**
     * Documents below this length are penalized
     */
    public int shortDocumentThreshold;

    public double shortDocumentPenalty;


    /**
     * Scaling factor associated with domain rank (unscaled rank value is 0-255; high is good)
     */
    public double domainRankBonus;

    /**
     * Scaling factor associated with document quality (unscaled rank value is 0-15; high is bad)
     */
    public double qualityPenalty;

    /**
     * Average sentence length values below this threshold are penalized, range [0-4), 2 or 3 is probably what you want
     */
    public int shortSentenceThreshold;

    /**
     * Magnitude of penalty for documents with low average sentence length
     */
    public double shortSentencePenalty;

    public double bm25Weight;
    public double tcfFirstPosition;
    public double tcfVerbatim;
    public double tcfProximity;


    public TemporalBias temporalBias;
    public double temporalBiasWeight;

    public boolean disablePenalties;
    public boolean exportDebugData;

    public ResultRankingParameters(Bm25Parameters bm25Params, int shortDocumentThreshold, double shortDocumentPenalty, double domainRankBonus, double qualityPenalty, int shortSentenceThreshold, double shortSentencePenalty, double bm25Weight, double tcfFirstPosition, double tcfVerbatim, double tcfProximity, TemporalBias temporalBias, double temporalBiasWeight, boolean disablePenalties, boolean exportDebugData) {
        this.bm25Params = bm25Params;
        this.shortDocumentThreshold = shortDocumentThreshold;
        this.shortDocumentPenalty = shortDocumentPenalty;
        this.domainRankBonus = domainRankBonus;
        this.qualityPenalty = qualityPenalty;
        this.shortSentenceThreshold = shortSentenceThreshold;
        this.shortSentencePenalty = shortSentencePenalty;
        this.bm25Weight = bm25Weight;
        this.tcfFirstPosition = tcfFirstPosition;
        this.tcfVerbatim = tcfVerbatim;
        this.tcfProximity = tcfProximity;
        this.temporalBias = temporalBias;
        this.temporalBiasWeight = temporalBiasWeight;
        this.disablePenalties = disablePenalties;
        this.exportDebugData = exportDebugData;
    }

    public static ResultRankingParameters sensibleDefaults() {
        return builder()
                .bm25Params(new Bm25Parameters(1.2, 0.5))
                .shortDocumentThreshold(2000)
                .shortDocumentPenalty(2.)
                .domainRankBonus(1 / 100.)
                .qualityPenalty(1 / 15.)
                .shortSentenceThreshold(2)
                .shortSentencePenalty(5)
                .bm25Weight(1.)
                .tcfVerbatim(1.)
                .tcfProximity(1.)
                .tcfFirstPosition(5)
                .temporalBias(TemporalBias.NONE)
                .temporalBiasWeight(5.0)
                .exportDebugData(false)
                .disablePenalties(false)
                .build();
    }

    public static ResultRankingParametersBuilder builder() {
        return new ResultRankingParametersBuilder();
    }

    public Bm25Parameters getBm25Params() {
        return this.bm25Params;
    }

    public int getShortDocumentThreshold() {
        return this.shortDocumentThreshold;
    }

    public double getShortDocumentPenalty() {
        return this.shortDocumentPenalty;
    }

    public double getDomainRankBonus() {
        return this.domainRankBonus;
    }

    public double getQualityPenalty() {
        return this.qualityPenalty;
    }

    public int getShortSentenceThreshold() {
        return this.shortSentenceThreshold;
    }

    public double getShortSentencePenalty() {
        return this.shortSentencePenalty;
    }

    public double getBm25Weight() {
        return this.bm25Weight;
    }

    public double getTcfFirstPosition() {
        return this.tcfFirstPosition;
    }

    public double getTcfVerbatim() {
        return this.tcfVerbatim;
    }

    public double getTcfProximity() {
        return this.tcfProximity;
    }

    public TemporalBias getTemporalBias() {
        return this.temporalBias;
    }

    public double getTemporalBiasWeight() {
        return this.temporalBiasWeight;
    }

    public boolean isDisablePenalties() { return this.disablePenalties; }

    public boolean isExportDebugData() {
        return this.exportDebugData;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResultRankingParameters that)) return false;

        return shortDocumentThreshold == that.shortDocumentThreshold && Double.compare(shortDocumentPenalty, that.shortDocumentPenalty) == 0 && Double.compare(domainRankBonus, that.domainRankBonus) == 0 && Double.compare(qualityPenalty, that.qualityPenalty) == 0 && shortSentenceThreshold == that.shortSentenceThreshold && Double.compare(shortSentencePenalty, that.shortSentencePenalty) == 0 && Double.compare(bm25Weight, that.bm25Weight) == 0 && Double.compare(tcfFirstPosition, that.tcfFirstPosition) == 0 && Double.compare(tcfVerbatim, that.tcfVerbatim) == 0 && Double.compare(tcfProximity, that.tcfProximity) == 0 && Double.compare(temporalBiasWeight, that.temporalBiasWeight) == 0 && exportDebugData == that.exportDebugData && Objects.equals(bm25Params, that.bm25Params) && temporalBias == that.temporalBias;
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(bm25Params);
        result = 31 * result + shortDocumentThreshold;
        result = 31 * result + Double.hashCode(shortDocumentPenalty);
        result = 31 * result + Double.hashCode(domainRankBonus);
        result = 31 * result + Double.hashCode(qualityPenalty);
        result = 31 * result + shortSentenceThreshold;
        result = 31 * result + Double.hashCode(shortSentencePenalty);
        result = 31 * result + Double.hashCode(bm25Weight);
        result = 31 * result + Double.hashCode(tcfFirstPosition);
        result = 31 * result + Double.hashCode(tcfVerbatim);
        result = 31 * result + Double.hashCode(tcfProximity);
        result = 31 * result + Objects.hashCode(temporalBias);
        result = 31 * result + Double.hashCode(temporalBiasWeight);
        result = 31 * result + Boolean.hashCode(disablePenalties);
        result = 31 * result + Boolean.hashCode(exportDebugData);
        return result;
    }

    public String toString() {
        return "ResultRankingParameters(bm25Params=" + this.getBm25Params() + ", shortDocumentThreshold=" + this.getShortDocumentThreshold() + ", shortDocumentPenalty=" + this.getShortDocumentPenalty() + ", domainRankBonus=" + this.getDomainRankBonus() + ", qualityPenalty=" + this.getQualityPenalty() + ", shortSentenceThreshold=" + this.getShortSentenceThreshold() + ", shortSentencePenalty=" + this.getShortSentencePenalty() + ", bm25Weight=" + this.getBm25Weight() + ", tcfFirstPosition=" + this.getTcfFirstPosition() + ", tcfVerbatim=" + this.getTcfVerbatim() + ", tcfProximity=" + this.getTcfProximity() + ", temporalBias=" + this.getTemporalBias() + ", temporalBiasWeight=" + this.getTemporalBiasWeight() + ", exportDebugData=" + this.isExportDebugData() + ")";
    }

    public enum TemporalBias {
        RECENT, OLD, NONE
    }

    public static class ResultRankingParametersBuilder {
        private Bm25Parameters bm25Params;
        private int shortDocumentThreshold;
        private double shortDocumentPenalty;
        private double domainRankBonus;
        private double qualityPenalty;
        private int shortSentenceThreshold;
        private double shortSentencePenalty;
        private double bm25Weight;
        private double tcfFirstPosition;
        private double tcfVerbatim;
        private double tcfProximity;
        private TemporalBias temporalBias;
        private double temporalBiasWeight;
        private boolean disablePenalties;
        private boolean exportDebugData;

        ResultRankingParametersBuilder() {
        }

        public ResultRankingParametersBuilder bm25Params(Bm25Parameters bm25Params) {
            this.bm25Params = bm25Params;
            return this;
        }

        public ResultRankingParametersBuilder shortDocumentThreshold(int shortDocumentThreshold) {
            this.shortDocumentThreshold = shortDocumentThreshold;
            return this;
        }

        public ResultRankingParametersBuilder shortDocumentPenalty(double shortDocumentPenalty) {
            this.shortDocumentPenalty = shortDocumentPenalty;
            return this;
        }

        public ResultRankingParametersBuilder domainRankBonus(double domainRankBonus) {
            this.domainRankBonus = domainRankBonus;
            return this;
        }

        public ResultRankingParametersBuilder qualityPenalty(double qualityPenalty) {
            this.qualityPenalty = qualityPenalty;
            return this;
        }

        public ResultRankingParametersBuilder shortSentenceThreshold(int shortSentenceThreshold) {
            this.shortSentenceThreshold = shortSentenceThreshold;
            return this;
        }

        public ResultRankingParametersBuilder shortSentencePenalty(double shortSentencePenalty) {
            this.shortSentencePenalty = shortSentencePenalty;
            return this;
        }

        public ResultRankingParametersBuilder bm25Weight(double bm25Weight) {
            this.bm25Weight = bm25Weight;
            return this;
        }

        public ResultRankingParametersBuilder tcfFirstPosition(double tcfFirstPosition) {
            this.tcfFirstPosition = tcfFirstPosition;
            return this;
        }

        public ResultRankingParametersBuilder tcfVerbatim(double tcfVerbatim) {
            this.tcfVerbatim = tcfVerbatim;
            return this;
        }

        public ResultRankingParametersBuilder tcfProximity(double tcfProximity) {
            this.tcfProximity = tcfProximity;
            return this;
        }

        public ResultRankingParametersBuilder temporalBias(TemporalBias temporalBias) {
            this.temporalBias = temporalBias;
            return this;
        }

        public ResultRankingParametersBuilder temporalBiasWeight(double temporalBiasWeight) {
            this.temporalBiasWeight = temporalBiasWeight;
            return this;
        }


        public ResultRankingParametersBuilder disablePenalties(boolean disablePenalties) {
            this.disablePenalties = disablePenalties;
            return this;
        }

        public ResultRankingParametersBuilder exportDebugData(boolean exportDebugData) {
            this.exportDebugData = exportDebugData;
            return this;
        }

        public ResultRankingParameters build() {
            return new ResultRankingParameters(this.bm25Params, this.shortDocumentThreshold, this.shortDocumentPenalty, this.domainRankBonus, this.qualityPenalty, this.shortSentenceThreshold, this.shortSentencePenalty, this.bm25Weight, this.tcfFirstPosition, this.tcfVerbatim, this.tcfProximity, this.temporalBias, this.temporalBiasWeight, this.disablePenalties, this.exportDebugData);
        }

    }
}

package nu.marginalia.api.searchquery.model.results;

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

    public boolean exportDebugData;

    public ResultRankingParameters(Bm25Parameters bm25Params, int shortDocumentThreshold, double shortDocumentPenalty, double domainRankBonus, double qualityPenalty, int shortSentenceThreshold, double shortSentencePenalty, double bm25Weight, double tcfFirstPosition, double tcfVerbatim, double tcfProximity, TemporalBias temporalBias, double temporalBiasWeight, boolean exportDebugData) {
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
        this.exportDebugData = exportDebugData;
    }

    public static ResultRankingParameters sensibleDefaults() {
        return builder()
                .bm25Params(new Bm25Parameters(1.2, 0.5))
                .shortDocumentThreshold(2000)
                .shortDocumentPenalty(2.)
                .domainRankBonus(1 / 25.)
                .qualityPenalty(1 / 15.)
                .shortSentenceThreshold(2)
                .shortSentencePenalty(5)
                .bm25Weight(1.)
                .tcfVerbatim(2.)
                .tcfProximity(1.)
                .tcfFirstPosition(5)
                .temporalBias(TemporalBias.NONE)
                .temporalBiasWeight(5.0)
                .exportDebugData(false)
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

    public boolean isExportDebugData() {
        return this.exportDebugData;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof ResultRankingParameters)) return false;
        final ResultRankingParameters other = (ResultRankingParameters) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$bm25Params = this.getBm25Params();
        final Object other$bm25Params = other.getBm25Params();
        if (this$bm25Params == null ? other$bm25Params != null : !this$bm25Params.equals(other$bm25Params))
            return false;
        if (this.getShortDocumentThreshold() != other.getShortDocumentThreshold()) return false;
        if (Double.compare(this.getShortDocumentPenalty(), other.getShortDocumentPenalty()) != 0) return false;
        if (Double.compare(this.getDomainRankBonus(), other.getDomainRankBonus()) != 0) return false;
        if (Double.compare(this.getQualityPenalty(), other.getQualityPenalty()) != 0) return false;
        if (this.getShortSentenceThreshold() != other.getShortSentenceThreshold()) return false;
        if (Double.compare(this.getShortSentencePenalty(), other.getShortSentencePenalty()) != 0) return false;
        if (Double.compare(this.getBm25Weight(), other.getBm25Weight()) != 0) return false;
        if (Double.compare(this.getTcfFirstPosition(), other.getTcfFirstPosition()) != 0) return false;
        if (Double.compare(this.getTcfVerbatim(), other.getTcfVerbatim()) != 0) return false;
        if (Double.compare(this.getTcfProximity(), other.getTcfProximity()) != 0) return false;
        final Object this$temporalBias = this.getTemporalBias();
        final Object other$temporalBias = other.getTemporalBias();
        if (this$temporalBias == null ? other$temporalBias != null : !this$temporalBias.equals(other$temporalBias))
            return false;
        if (Double.compare(this.getTemporalBiasWeight(), other.getTemporalBiasWeight()) != 0) return false;
        if (this.isExportDebugData() != other.isExportDebugData()) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ResultRankingParameters;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $bm25Params = this.getBm25Params();
        result = result * PRIME + ($bm25Params == null ? 43 : $bm25Params.hashCode());
        result = result * PRIME + this.getShortDocumentThreshold();
        final long $shortDocumentPenalty = Double.doubleToLongBits(this.getShortDocumentPenalty());
        result = result * PRIME + (int) ($shortDocumentPenalty >>> 32 ^ $shortDocumentPenalty);
        final long $domainRankBonus = Double.doubleToLongBits(this.getDomainRankBonus());
        result = result * PRIME + (int) ($domainRankBonus >>> 32 ^ $domainRankBonus);
        final long $qualityPenalty = Double.doubleToLongBits(this.getQualityPenalty());
        result = result * PRIME + (int) ($qualityPenalty >>> 32 ^ $qualityPenalty);
        result = result * PRIME + this.getShortSentenceThreshold();
        final long $shortSentencePenalty = Double.doubleToLongBits(this.getShortSentencePenalty());
        result = result * PRIME + (int) ($shortSentencePenalty >>> 32 ^ $shortSentencePenalty);
        final long $bm25Weight = Double.doubleToLongBits(this.getBm25Weight());
        result = result * PRIME + (int) ($bm25Weight >>> 32 ^ $bm25Weight);
        final long $tcfFirstPosition = Double.doubleToLongBits(this.getTcfFirstPosition());
        result = result * PRIME + (int) ($tcfFirstPosition >>> 32 ^ $tcfFirstPosition);
        final long $tcfVerbatim = Double.doubleToLongBits(this.getTcfVerbatim());
        result = result * PRIME + (int) ($tcfVerbatim >>> 32 ^ $tcfVerbatim);
        final long $tcfProximity = Double.doubleToLongBits(this.getTcfProximity());
        result = result * PRIME + (int) ($tcfProximity >>> 32 ^ $tcfProximity);
        final Object $temporalBias = this.getTemporalBias();
        result = result * PRIME + ($temporalBias == null ? 43 : $temporalBias.hashCode());
        final long $temporalBiasWeight = Double.doubleToLongBits(this.getTemporalBiasWeight());
        result = result * PRIME + (int) ($temporalBiasWeight >>> 32 ^ $temporalBiasWeight);
        result = result * PRIME + (this.isExportDebugData() ? 79 : 97);
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

        public ResultRankingParametersBuilder exportDebugData(boolean exportDebugData) {
            this.exportDebugData = exportDebugData;
            return this;
        }

        public ResultRankingParameters build() {
            return new ResultRankingParameters(this.bm25Params, this.shortDocumentThreshold, this.shortDocumentPenalty, this.domainRankBonus, this.qualityPenalty, this.shortSentenceThreshold, this.shortSentencePenalty, this.bm25Weight, this.tcfFirstPosition, this.tcfVerbatim, this.tcfProximity, this.temporalBias, this.temporalBiasWeight, this.exportDebugData);
        }

        public String toString() {
            return "ResultRankingParameters.ResultRankingParametersBuilder(bm25Params=" + this.bm25Params + ", shortDocumentThreshold=" + this.shortDocumentThreshold + ", shortDocumentPenalty=" + this.shortDocumentPenalty + ", domainRankBonus=" + this.domainRankBonus + ", qualityPenalty=" + this.qualityPenalty + ", shortSentenceThreshold=" + this.shortSentenceThreshold + ", shortSentencePenalty=" + this.shortSentencePenalty + ", bm25Weight=" + this.bm25Weight + ", tcfFirstPosition=" + this.tcfFirstPosition + ", tcfVerbatim=" + this.tcfVerbatim + ", tcfProximity=" + this.tcfProximity + ", temporalBias=" + this.temporalBias + ", temporalBiasWeight=" + this.temporalBiasWeight + ", exportDebugData=" + this.exportDebugData + ")";
        }
    }
}

package nu.marginalia.api.searchquery.model.results;

import nu.marginalia.api.searchquery.RpcResultRankingParameters;
import nu.marginalia.api.searchquery.RpcTemporalBias;

public class PrototypeRankingParameters {

    /** These are the default ranking parameters that are used when no parameters are specified. */

    private static final RpcResultRankingParameters _sensibleDefaults = RpcResultRankingParameters.newBuilder()
                .setBm25B(0.5)
                .setBm25K(1.2)
                .setShortDocumentThreshold(2000)
                .setShortDocumentPenalty(2.)
                .setDomainRankBonus(1 / 100.)
                .setQualityPenalty(1 / 15.)
                .setShortSentenceThreshold(2)
                .setShortSentencePenalty(5)
                .setBm25Weight(1.)
                .setTcfVerbatimWeight(1.)
                .setTcfProximityWeight(1.)
                .setTcfFirstPositionWeight(5)
                .setTemporalBias(RpcTemporalBias.newBuilder().setBias(RpcTemporalBias.Bias.NONE))
                .setTemporalBiasWeight(5.0)
                .setExportDebugData(false)
                .setDisablePenalties(false)
                .build();

    public static RpcResultRankingParameters sensibleDefaults() {
        return _sensibleDefaults;
    }

}

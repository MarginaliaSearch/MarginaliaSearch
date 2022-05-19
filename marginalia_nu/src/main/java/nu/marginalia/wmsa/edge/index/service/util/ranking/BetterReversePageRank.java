package nu.marginalia.wmsa.edge.index.service.util.ranking;


import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;

public class BetterReversePageRank extends RankingAlgorithm {


    public BetterReversePageRank(HikariDataSource dataSource, String... origins) throws IOException {
        super(dataSource, origins);
    }

    @Override
    RankVector createNewRankVector(RankVector rank) {

        double rankNorm = rank.norm();
        RankVector newRank = new RankVector(0);

        for (int domainId = 0; domainId < domainIndexToId.size(); domainId++) {

            var links = linkDataSrc2Dest[domainId];
            double newRankValue = 0;

            if (links != null && links.size() > 0) {


                for (int j = 0; j < links.size(); j++) {
                    var revLinks = linkDataDest2Src[links.getQuick(j)];
                    newRankValue += rank.get(links.getQuick(j)) / revLinks.size();
                }
            }

            newRank.set(domainId, 0.85*newRankValue/rankNorm);
        }

        return newRank;
    }

    @Override
    void adjustRankVector(RankVector vector, double dNorm, double oldNorm) {
        originDomainIds.forEach(id -> vector.increment(id, 1.0 / originDomainIds.size()));
    }

}

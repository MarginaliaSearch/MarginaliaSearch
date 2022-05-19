package nu.marginalia.util.ranking;


import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;

public class BuggyStandardPageRank extends RankingAlgorithm {

    public BuggyStandardPageRank(HikariDataSource dataSource, String... origins) throws IOException {
        super(dataSource, origins);
    }

    @Override
    RankingAlgorithm.RankVector createNewRankVector(RankingAlgorithm.RankVector rank) {
        RankVector newRank = new RankVector(0);

        for (int domainId = 0; domainId < domainIndexToId.size(); domainId++) {

            var links = linkDataSrc2Dest[domainId];
            double newRankValue = 0;

            if (links != null && links.size() > 0) {
                for (int j = 0; j < links.size(); j++) {
                    int linkedDomain = links.getQuick(j);

                    int linkSize = 1;
                    var bl = linkDataSrc2Dest[linkedDomain];
                    if (bl != null) {
                        linkSize = bl.size();
                    }

                    newRankValue += rank.get(linkedDomain) / linkSize;

                }
            }

            newRank.set(domainId,  0.85 * newRankValue);
        }
        return newRank;
    }

    @Override
    void adjustRankVector(RankingAlgorithm.RankVector vector, double dNorm, double oldNorm) {
        originDomainIds.forEach(id -> vector.increment(id, dNorm/originDomainIds.size()));
        vector.incrementAll(0.14*dNorm/vector.size());
    }

}

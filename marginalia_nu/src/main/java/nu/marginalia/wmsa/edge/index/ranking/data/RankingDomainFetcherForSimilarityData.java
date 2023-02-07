package nu.marginalia.wmsa.edge.index.ranking.data;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.wmsa.edge.dbcommon.EdgeDomainBlacklistImpl;

import java.sql.SQLException;
import java.util.function.Consumer;

public class RankingDomainFetcherForSimilarityData extends RankingDomainFetcher {
    public RankingDomainFetcherForSimilarityData(HikariDataSource dataSource, EdgeDomainBlacklistImpl blacklist) {
        super(dataSource, blacklist);
    }

    public void eachDomainLink(DomainLinkConsumer consumer) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT DOMAIN_ID, NEIGHBOR_ID, RELATEDNESS FROM EC_DOMAIN_NEIGHBORS_2"))
        {
            stmt.setFetchSize(10000);

            var rsp = stmt.executeQuery();

            while (rsp.next()) {
                int src = rsp.getInt(1);
                int dst = rsp.getInt(2);

                // these "links" are bidi
                consumer.accept(src, dst);
                consumer.accept(dst, src);
            }
        }
        catch (SQLException ex) {
            logger.error("Failed to fetch domain links", ex);
        }
    }

    public void getDomains(Consumer<RankingDomainData> consumer) {
//        String query =
//               """
//                   SELECT EC_DOMAIN.ID,DOMAIN_NAME,DOMAIN_ALIAS,STATE,COALESCE(KNOWN_URLS, 0)
//                   FROM EC_DOMAIN
//                   LEFT JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID
//                   INNER JOIN EC_DOMAIN_LINK ON DEST_DOMAIN_ID=EC_DOMAIN.ID
//                   WHERE SOURCE_DOMAIN_ID!=DEST_DOMAIN_ID
//                   GROUP BY EC_DOMAIN.ID
//                   HAVING COUNT(SOURCE_DOMAIN_ID)>5
//               """;

        String query =
                """
                    SELECT EC_DOMAIN.ID,DOMAIN_NAME,DOMAIN_ALIAS,STATE,COALESCE(KNOWN_URLS, 0)
                    FROM EC_DOMAIN 
                    INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID 
                    GROUP BY EC_DOMAIN.ID 
                """;

        getDomains(query, consumer);
    }


    public void getPeripheralDomains(Consumer<RankingDomainData> consumer) {
    }

}

package nu.marginalia.ranking.data;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.db.DomainBlacklistImpl;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.function.Consumer;

@Singleton
public class RankingDomainFetcherForSimilarityData extends RankingDomainFetcher {
    final boolean hasData;

    @Inject
    public RankingDomainFetcherForSimilarityData(HikariDataSource dataSource, DomainBlacklistImpl blacklist) {
        super(dataSource, blacklist);

        hasData = isDomainNeighborTablePopulated(dataSource);
    }

    private static boolean isDomainNeighborTablePopulated(HikariDataSource dataSource) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT DOMAIN_ID FROM EC_DOMAIN_NEIGHBORS_2 LIMIT 1")) {

            return rs.next();
        }
        catch (SQLException ex) {
            LoggerFactory
                    .getLogger(RankingDomainFetcherForSimilarityData.class)
                    .error("Failed to get count from EC_DOMAIN_NEIGHBORS_2", ex);
            return false;
        }
    }
    public boolean hasData() {
        return hasData;
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

        String query;
        if (getNames) {
            query =
                    """
                        SELECT EC_DOMAIN.ID,DOMAIN_NAME,DOMAIN_ALIAS,STATE,COALESCE(KNOWN_URLS, 0)
                        FROM EC_DOMAIN 
                        INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID 
                        GROUP BY EC_DOMAIN.ID 
                    """;
        }
        else {
            query =
                    """
                        SELECT EC_DOMAIN.ID,"",DOMAIN_ALIAS,STATE,COALESCE(KNOWN_URLS, 0)
                        FROM EC_DOMAIN 
                        INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID 
                        GROUP BY EC_DOMAIN.ID 
                    """;
        }

        getDomains(query, consumer);
    }


    public void getPeripheralDomains(Consumer<RankingDomainData> consumer) {
        // This is not relevant for this variant of pagerank since it is bidirectional
    }

}

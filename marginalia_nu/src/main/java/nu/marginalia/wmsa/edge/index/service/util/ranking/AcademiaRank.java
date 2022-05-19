package nu.marginalia.wmsa.edge.index.service.util.ranking;

import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

public class AcademiaRank {
    private final TIntArrayList result;
    private static final Logger logger = LoggerFactory.getLogger(AcademiaRank.class);

    public AcademiaRank(HikariDataSource ds, String... origins) throws IOException {

        TIntList rankingResults = new BetterStandardPageRank(ds, origins).pageRank(100_000);
        TIntIntHashMap idToRanking = new TIntIntHashMap(100_000, 0.5f, -1, 1_000_000_000);

        for (int i = 0; i < rankingResults.size(); i++) {
            idToRanking.put(rankingResults.get(i), i);
        }

        result = new TIntArrayList(10000);
        try (var conn = ds.getConnection();
            var stmt = conn.prepareStatement("select EC_DOMAIN.ID,COUNT(SOURCE_DOMAIN_ID) AS CNT from EC_DOMAIN INNER JOIN DOMAIN_METADATA ON DOMAIN_METADATA.ID=EC_DOMAIN.ID INNER JOIN EC_DOMAIN_LINK ON EC_DOMAIN_LINK.DEST_DOMAIN_ID=EC_DOMAIN.ID WHERE INDEXED>0 AND STATE>=0 AND STATE<2 AND ((VISITED_URLS>1000+1500*RANK AND RANK<1) OR (GOOD_URLS>1000 AND URL_PART LIKE '%edu')) GROUP BY EC_DOMAIN.ID HAVING CNT<1500 ORDER BY RANK ASC")) {

            stmt.setFetchSize(1000);
            var rsp = stmt.executeQuery();
            while (rsp.next()) {
                result.add(rsp.getInt(1));
            }
        }
        catch (SQLException ex) {
            logger.error("SQL error", ex);
        }

        int[] internalArray = result.toArray();
        IntArrays.quickSort(internalArray, (a,b) -> idToRanking.get(a) - idToRanking.get(b));
        result.set(0, internalArray);
    }

    public TIntArrayList getResult() {
        return result;
    }
}

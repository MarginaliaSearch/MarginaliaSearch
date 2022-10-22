package nu.marginalia.wmsa.edge.converting;

import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.map.hash.TIntIntHashMap;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;

import java.sql.SQLException;

public class UpdateDomainStatistics {
    private final HikariDataSource dataSource;

    public UpdateDomainStatistics(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static void main(String... args) throws SQLException {
        new UpdateDomainStatistics(new DatabaseModule().provideConnection()).run();
    }

    public void run() throws SQLException {

        // This looks weird, but it's actually much faster than doing the computations with SQL queries
        //
        // ... in part because we can assume the data is immutable and don't mind consuming egregious
        // resources

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var domainInfoQuery = conn.prepareStatement("SELECT DOMAIN_ID, VISITED, STATE='ok' FROM EC_URL");
             var insertDomainInfo = conn.prepareStatement("INSERT INTO DOMAIN_METADATA(ID,KNOWN_URLS,VISITED_URLS,GOOD_URLS) VALUES (?, ?, ?, ?)")
        ) {

            stmt.executeUpdate("DELETE FROM DOMAIN_METADATA");

            TIntIntHashMap knownUrls = new TIntIntHashMap(1_000_000, 0.75f, 0, 0);
            TIntIntHashMap visitedUrls = new TIntIntHashMap(1_000_000, 0.75f, 0, 0);
            TIntIntHashMap goodUrls = new TIntIntHashMap(1_000_000, 0.75f, 0, 0);

            domainInfoQuery.setFetchSize(10_000);
            var rsp = domainInfoQuery.executeQuery();
            while (rsp.next()) {
                int domainId = rsp.getInt(1);
                boolean visited = rsp.getBoolean(2);
                boolean stateOk = rsp.getBoolean(3);

                knownUrls.adjustOrPutValue(domainId, 1, 1);
                if (visited) {
                    visitedUrls.adjustOrPutValue(domainId, 1, 1);
                    if (stateOk) {
                        goodUrls.adjustOrPutValue(domainId, 1, 1);
                    }
                }
            }

            int i = 0;
            for (int domainId : knownUrls.keys()) {
                insertDomainInfo.setInt(1, domainId);
                insertDomainInfo.setInt(2, knownUrls.get(domainId));
                insertDomainInfo.setInt(3, visitedUrls.get(domainId));
                insertDomainInfo.setInt(4, goodUrls.get(domainId));
                insertDomainInfo.addBatch();
                if ((++i % 1000) == 0) {
                    insertDomainInfo.executeBatch();
                }
            }
            if ((i % 1000) != 0) {
                insertDomainInfo.executeBatch();
            }
        }
    }
}

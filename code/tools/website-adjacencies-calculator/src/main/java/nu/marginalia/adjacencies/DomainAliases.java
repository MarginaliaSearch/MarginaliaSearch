package nu.marginalia.adjacencies;

import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.map.hash.TIntIntHashMap;

import java.sql.SQLException;

public class DomainAliases {

    private final TIntIntHashMap aliasMap = new TIntIntHashMap(100_000, 0.75f, -1, -1);

    public DomainAliases(HikariDataSource dataSource) throws SQLException  {
        try (
                var conn = dataSource.getConnection();
                var aliasStmt = conn.prepareStatement("SELECT ID, DOMAIN_ALIAS FROM EC_DOMAIN WHERE DOMAIN_ALIAS IS NOT NULL")
        ) {
            aliasStmt.setFetchSize(10_000);
            var rsp = aliasStmt.executeQuery();
            while (rsp.next()) {
                aliasMap.put(rsp.getInt(1), rsp.getInt(2));
            }
        }

    }


    public int deAlias(int id) {
        int val = aliasMap.get(id);
        if (val < 0)
            return id;
        return val;
    }

    public boolean isNotAliased(int id) {
        return !aliasMap.containsKey(id);
    }
    public boolean isAliased(int id) {
        return aliasMap.containsKey(id);
    }
}

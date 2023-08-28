package nu.marginalia.loading.loader;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.map.hash.TObjectIntHashMap;
import nu.marginalia.model.EdgeDomain;

import java.sql.SQLException;

import static java.sql.Statement.SUCCESS_NO_INFO;

public class OldDomains {

    private final TObjectIntHashMap<EdgeDomain> knownDomains = new TObjectIntHashMap<>(100_000, 0.75f, -1);

    @Inject
    public OldDomains(HikariDataSource dataSource) {
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement("""
                    SELECT DOMAIN_NAME, ID FROM EC_DOMAIN
                    """))
            {
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    knownDomains.put(new EdgeDomain(rs.getString(1)), rs.getInt(2));
                }
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException("Failed to set up loader", ex);
        }
    }

    public int getId(EdgeDomain domain) {
        return knownDomains.get(domain);
    }

    public void add(EdgeDomain domain, int id) {
        knownDomains.put(domain, id);
    }
}

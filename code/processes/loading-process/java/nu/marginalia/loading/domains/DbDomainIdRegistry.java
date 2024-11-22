package nu.marginalia.loading.domains;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.model.EdgeDomain;

import java.sql.Statement;

public class DbDomainIdRegistry implements DomainIdRegistry {
    private final HikariDataSource dataSource;

    public DbDomainIdRegistry(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int getDomainId(String domainName) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {

            stmt.setString(1, domainName);
            var rsp = stmt.executeQuery();
            if (rsp.next()) {
                return rsp.getInt(1);
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to query domain ID", e);
        }

        // Insert the domain if it doesn't exist (unlikely)
        try (var conn = dataSource.getConnection();
            var stmt = conn.prepareStatement("INSERT IGNORE INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {

            var domain = new EdgeDomain(domainName);

            stmt.setString(1, domain.toString());
            stmt.setString(2, domain.getTopDomain());
            stmt.setInt(3, 0); // "up for grabs" node affinity
            stmt.executeUpdate();

            var gk = stmt.getGeneratedKeys();
            if (gk.next()) {
                return gk.getInt(1);
            }
            else {
                // recurse in the doubly unlikely event that the domain was inserted by another thread
                return getDomainId(domainName);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void add(String domainName, int id) {
        throw new UnsupportedOperationException("Not implemented");
    }
}

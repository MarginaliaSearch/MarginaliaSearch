package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.model.BlacklistedDomainModel;
import nu.marginalia.model.EdgeDomain;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ControlBlacklistService {

    private final HikariDataSource dataSource;

    @Inject
    public ControlBlacklistService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void addToBlacklist(EdgeDomain domain, String comment) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT IGNORE INTO EC_DOMAIN_BLACKLIST (URL_DOMAIN, COMMENT) VALUES (?, ?)
                     """)) {
            stmt.setString(1, domain.toString());
            stmt.setString(2, comment);
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void removeFromBlacklist(EdgeDomain domain) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     DELETE FROM EC_DOMAIN_BLACKLIST WHERE URL_DOMAIN=?
                     """)) {
            stmt.setString(1, domain.toString());
            stmt.addBatch();
            stmt.setString(1, domain.domain);
            stmt.addBatch();
            stmt.executeBatch();
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<BlacklistedDomainModel> lastNAdditions(int n) {
        final List<BlacklistedDomainModel> ret = new ArrayList<>(n);

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT URL_DOMAIN, COMMENT
                     FROM EC_DOMAIN_BLACKLIST
                     ORDER BY ID DESC
                     LIMIT ?
                     """)) {
            stmt.setInt(1, n);

            var rs = stmt.executeQuery();
            while (rs.next()) {
                ret.add(new BlacklistedDomainModel(
                            new EdgeDomain(rs.getString("URL_DOMAIN")),
                            rs.getString("COMMENT")
                        )
                );
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return ret;

    }
}

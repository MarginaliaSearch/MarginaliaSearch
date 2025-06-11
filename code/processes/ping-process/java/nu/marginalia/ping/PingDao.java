package nu.marginalia.ping;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.ping.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Singleton
public class PingDao {
    private final HikariDataSource dataSource;
    private static final Gson gson = GsonFactory.get();
    private static final Logger logger = LoggerFactory.getLogger(PingDao.class);

    @Inject
    public PingDao(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void write(WritableModel model) {
        write(List.of(model));
    }

    public void write(Collection<WritableModel> models) {
        logger.debug("Writing: {}", models);

        try (var conn = dataSource.getConnection()) {

            // Don't bother with a transaction if there's only one model to write.
            if (models.size() <= 1) {
                for (WritableModel model : models) {
                    model.write(conn);
                }
            }
            else { // If there are multiple models, use a transaction to ensure atomicity.
                conn.setAutoCommit(false);
                try {
                    for (WritableModel model : models) {
                        model.write(conn);
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to write model", e);
        }
    }

    public void scheduleDnsUpdate(String rootDomainName, Instant timestamp, int priority) throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("""
            UPDATE DOMAIN_DNS_INFORMATION
            SET TS_NEXT_DNS_CHECK = ?, DNS_CHECK_PRIORITY = ?
            WHERE ROOT_DOMAIN_NAME = ?
            """)) {

            ps.setTimestamp(1, java.sql.Timestamp.from(timestamp));
            ps.setInt(2, priority);
            ps.setString(3, rootDomainName);
            ps.executeUpdate();
        }
    }

    public List<DomainReference> getNewDomains(int nodeId, int cnt) throws SQLException {
        List<DomainReference> domains = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("""
            SELECT domain_id, domain_name
            FROM EC_DOMAIN 
            LEFT JOIN DOMAIN_AVAILABILITY_INFORMATION 
            ON EC_DOMAIN.domain_id = DOMAIN_AVAILABILITY_INFORMATION.domain_id
            WHERE DOMAIN_AVAILABILITY_INFORMATION.server_available IS NULL
            AND EC_DOMAIN.NODE_ID = ?
            LIMIT ?
            """))
        {
            ps.setInt(1, nodeId);
            ps.setInt(2, cnt);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                domains.add(new DomainReference(rs.getInt("domain_id"), nodeId, rs.getString("domain_name").toLowerCase()));
            }
        }

        return domains;
    }

    public DomainAvailabilityRecord getDomainPingStatus(int domainId) throws SQLException {

        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("SELECT * FROM DOMAIN_AVAILABILITY_INFORMATION WHERE domain_id = ?")) {

            ps.setInt(1, domainId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new DomainAvailabilityRecord(rs);
            } else {
                return null; // or throw an exception if preferred
            }
        }

    }

    public DomainSecurityRecord getDomainSecurityInformation(int domainId) throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("SELECT * FROM DOMAIN_SECURITY_INFORMATION WHERE domain_id = ?")) {

            ps.setInt(1, domainId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new DomainSecurityRecord(rs);
            } else {
                return null; // or throw an exception if preferred
            }
        }
    }

    public DomainDnsRecord getDomainDnsRecord(int dnsRootDomainId) throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("SELECT * FROM DOMAIN_DNS_INFORMATION WHERE DNS_ROOT_DOMAIN_ID = ?")) {

            ps.setObject(1, dnsRootDomainId, java.sql.Types.INTEGER);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new DomainDnsRecord(rs);
            } else {
                return null; // or throw an exception if preferred
            }
        }
    }

    public DomainDnsRecord getDomainDnsRecord(String rootDomainName) throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("SELECT * FROM DOMAIN_DNS_INFORMATION WHERE ROOT_DOMAIN_NAME = ?")) {

            ps.setString(1, rootDomainName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new DomainDnsRecord(rs);
            } else {
                return null; // or throw an exception if preferred
            }
        }
    }

    public List<HistoricalAvailabilityData> getNextDomainPingStatuses(int count, int nodeId) throws SQLException {
        List<HistoricalAvailabilityData> domainAvailabilityRecords = new ArrayList<>(count);

        var query = """
            SELECT DOMAIN_AVAILABILITY_INFORMATION.*, DOMAIN_SECURITY_INFORMATION.*, EC_DOMAIN.DOMAIN_NAME FROM DOMAIN_AVAILABILITY_INFORMATION
                LEFT JOIN DOMAIN_SECURITY_INFORMATION
                ON DOMAIN_AVAILABILITY_INFORMATION.DOMAIN_ID = DOMAIN_SECURITY_INFORMATION.DOMAIN_ID
                INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID = DOMAIN_AVAILABILITY_INFORMATION.DOMAIN_ID
            WHERE NEXT_SCHEDULED_UPDATE <= ? AND DOMAIN_AVAILABILITY_INFORMATION.NODE_ID = ?
            ORDER BY NEXT_SCHEDULED_UPDATE ASC
            LIMIT ?
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(query)) {
            // Use Java time since this is how we generate the timestamps in the ping process
            // to avoid timezone weirdness.
            ps.setTimestamp(1, java.sql.Timestamp.from(Instant.now()));
            ps.setInt(2, nodeId);
            ps.setInt(3, count);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String domainName = rs.getString("EC_DOMAIN.DOMAIN_NAME");
                var domainAvailabilityRecord = new DomainAvailabilityRecord(rs);
                if (rs.getObject("DOMAIN_SECURITY_INFORMATION.DOMAIN_ID", Integer.class) != null) {
                    var securityRecord = new DomainSecurityRecord(rs);
                    domainAvailabilityRecords.add(
                        new HistoricalAvailabilityData.AvailabilityAndSecurity(domainName, domainAvailabilityRecord, securityRecord)
                    );
                } else {
                    domainAvailabilityRecords.add(new HistoricalAvailabilityData.JustAvailability(domainName, domainAvailabilityRecord));
                }
            }
        }
        return domainAvailabilityRecords;
    }

    public List<DomainDnsRecord> getNextDnsDomainRecords(int count, int nodeId) throws SQLException {
        List<DomainDnsRecord> domainDnsRecords = new ArrayList<>(count);

        var query = """
            SELECT * FROM DOMAIN_DNS_INFORMATION
            WHERE TS_NEXT_DNS_CHECK <= ? AND NODE_AFFINITY = ?
            ORDER BY DNS_CHECK_PRIORITY DESC, TS_NEXT_DNS_CHECK ASC
            LIMIT ?
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(query)) {
            ps.setTimestamp(1, java.sql.Timestamp.from(Instant.now()));
            ps.setInt(2, nodeId);
            ps.setInt(3, count);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                domainDnsRecords.add(new DomainDnsRecord(rs));
            }
        }
        return domainDnsRecords;
    }

    public List<DomainReference> getOrphanedDomains(int nodeId) {
        List<DomainReference> orphanedDomains = new ArrayList<>();
        try (var conn = dataSource.getConnection();
            var stmt = conn.prepareStatement("""
                SELECT e.DOMAIN_NAME, e.ID
                FROM EC_DOMAIN e
                LEFT JOIN DOMAIN_AVAILABILITY_INFORMATION d ON e.ID = d.DOMAIN_ID
                WHERE d.DOMAIN_ID IS NULL AND e.NODE_AFFINITY = ?;
                """)) {
            stmt.setInt(1, nodeId);
            stmt.setFetchSize(10_000);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String domainName = rs.getString("DOMAIN_NAME");
                int domainId = rs.getInt("ID");

                orphanedDomains.add(new DomainReference(domainId, nodeId, domainName));
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve orphaned domains", e);
        }

        return orphanedDomains;
    }

    public List<String> getOrphanedRootDomains(int nodeId) {
        List<String> orphanedDomains = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT DISTINCT(DOMAIN_TOP)
                FROM EC_DOMAIN e
                LEFT JOIN DOMAIN_DNS_INFORMATION d ON e.DOMAIN_TOP = d.ROOT_DOMAIN_NAME
                WHERE d.ROOT_DOMAIN_NAME IS NULL AND e.NODE_AFFINITY = ?;
                """)) {
            stmt.setInt(1, nodeId);
            stmt.setFetchSize(10_000);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String domainName = rs.getString("DOMAIN_TOP");
                orphanedDomains.add(domainName.toLowerCase());
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve orphaned domains", e);
        }

        return orphanedDomains;
    }
}

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
import java.util.Objects;

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

    public DomainDnsRecord getDomainDnsRecord(long dnsRootDomainId) throws SQLException {
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

    public HistoricalAvailabilityData getHistoricalAvailabilityData(long domainId) throws SQLException {
        var query = """
            SELECT EC_DOMAIN.ID, EC_DOMAIN.DOMAIN_NAME, EC_DOMAIN.NODE_AFFINITY, DOMAIN_AVAILABILITY_INFORMATION.*, DOMAIN_SECURITY_INFORMATION.*
                FROM EC_DOMAIN
                LEFT JOIN DOMAIN_SECURITY_INFORMATION ON DOMAIN_SECURITY_INFORMATION.DOMAIN_ID = EC_DOMAIN.ID
                LEFT JOIN DOMAIN_AVAILABILITY_INFORMATION ON DOMAIN_AVAILABILITY_INFORMATION.DOMAIN_ID = EC_DOMAIN.ID
                WHERE EC_DOMAIN.ID = ?
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(query)) {

            ps.setLong(1, domainId);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String domainName = rs.getString("EC_DOMAIN.DOMAIN_NAME");

                DomainAvailabilityRecord dar;
                DomainSecurityRecord dsr;

                if (rs.getObject("DOMAIN_SECURITY_INFORMATION.DOMAIN_ID", Integer.class) != null)
                    dsr = new DomainSecurityRecord(rs);
                else
                    dsr = null;

                if (rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.DOMAIN_ID", Integer.class) != null)
                    dar = new DomainAvailabilityRecord(rs);
                else
                    dar = null;

                if (dar == null) {
                    return new HistoricalAvailabilityData.JustDomainReference(new DomainReference(
                            rs.getInt("EC_DOMAIN.ID"),
                            rs.getInt("EC_DOMAIN.NODE_AFFINITY"),
                            domainName.toLowerCase()
                    ));
                }
                else {
                    if (dsr != null) {
                        return new HistoricalAvailabilityData.AvailabilityAndSecurity(domainName, dar, dsr);
                    } else {
                        return new HistoricalAvailabilityData.JustAvailability(domainName, dar);
                    }
                }
            }
        }

        return null;
    }

    public List<UpdateSchedule.UpdateJob<DomainReference, HistoricalAvailabilityData>> getDomainUpdateSchedule(int nodeId) {
        List<UpdateSchedule.UpdateJob<DomainReference, HistoricalAvailabilityData>> updateJobs = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("""
                SELECT ID, DOMAIN_NAME, NEXT_SCHEDULED_UPDATE
                FROM EC_DOMAIN
                LEFT JOIN DOMAIN_AVAILABILITY_INFORMATION
                ON EC_DOMAIN.ID = DOMAIN_AVAILABILITY_INFORMATION.DOMAIN_ID
                WHERE NODE_AFFINITY = ?
                """)) {
            ps.setFetchSize(10_000);
            ps.setInt(1, nodeId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int domainId = rs.getInt("ID");
                String domainName = rs.getString("DOMAIN_NAME");
                var ts = rs.getTimestamp("NEXT_SCHEDULED_UPDATE");
                Instant nextUpdate = ts == null ? Instant.now() : ts.toInstant();

                var ref = new DomainReference(domainId, nodeId, domainName.toLowerCase());
                updateJobs.add(new UpdateSchedule.UpdateJob<>(ref, nextUpdate));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve domain update schedule", e);
        }

        logger.info("Found {} availability update jobs for node {}", updateJobs.size(), nodeId);

        return updateJobs;
    }

    public List<UpdateSchedule.UpdateJob<RootDomainReference, RootDomainReference>> getDnsUpdateSchedule(int nodeId) {
        List<UpdateSchedule.UpdateJob<RootDomainReference, RootDomainReference>> updateJobs = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("""
                SELECT DISTINCT(DOMAIN_TOP),DOMAIN_DNS_INFORMATION.* FROM EC_DOMAIN
                LEFT JOIN DOMAIN_DNS_INFORMATION ON ROOT_DOMAIN_NAME = DOMAIN_TOP
                WHERE EC_DOMAIN.NODE_AFFINITY = ?
                """)) {
            ps.setFetchSize(10_000);
            ps.setInt(1, nodeId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Long dnsRootDomainId = rs.getObject("DOMAIN_DNS_INFORMATION.DNS_ROOT_DOMAIN_ID", Long.class);
                String rootDomainName = rs.getString("DOMAIN_TOP");

                if (dnsRootDomainId == null) {
                    updateJobs.add(
                            new UpdateSchedule.UpdateJob<>(
                            new RootDomainReference.ByName(rootDomainName),
                            Instant.now())
                    );
                }
                else {
                    var record = new DomainDnsRecord(rs);
                    updateJobs.add(new UpdateSchedule.UpdateJob<>(
                            new RootDomainReference.ByIdAndName(dnsRootDomainId, rootDomainName),
                            Objects.requireNonNullElseGet(record.tsNextScheduledUpdate(), Instant::now))
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve DNS update schedule", e);
        }

        logger.info("Found {} dns update jobs for node {}", updateJobs.size(), nodeId);

        return updateJobs;
    }
}

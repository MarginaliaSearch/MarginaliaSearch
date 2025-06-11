package nu.marginalia.ping.model;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

public record DomainAvailabilityEvent(
    int domainId,
    int nodeId,
    boolean available,
    AvailabilityOutageType outageType, // e.g., 'TIMEOUT', 'DNS_ERROR', etc.
    Integer httpStatusCode, // Nullable, as it may not always be applicable
    String errorMessage, // Specific error details
    Instant tsUpdate // Timestamp of the last update
) implements WritableModel {

    @Override
    public void write(Connection conn) throws SQLException {
        try (var ps = conn.prepareStatement("""
            INSERT INTO DOMAIN_AVAILABILITY_EVENTS (
                domain_id,
                node_id,
                available,
                outage_type,
                http_status_code,
                error_message,
                ts_change
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """))
        {
            ps.setInt(1, domainId());
            ps.setInt(2, nodeId());
            ps.setBoolean(3, available());
            ps.setString(4, outageType().name());
            if (httpStatusCode() == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, httpStatusCode());
            }
            if (errorMessage() == null) {
                ps.setNull(6, java.sql.Types.VARCHAR);
            } else {
                ps.setString(6, errorMessage());
            }
            ps.setTimestamp(7, java.sql.Timestamp.from(tsUpdate()));
            ps.executeUpdate();
        }
    }
}

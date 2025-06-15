package nu.marginalia.ping.model;

import nu.marginalia.ping.util.JsonObject;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

public record DomainSecurityEvent(
        int domainId,
        int nodeId,
        Instant tsChange,
        boolean asnChanged,
        boolean certificateFingerprintChanged,
        boolean certificateProfileChanged,
        boolean certificateSanChanged,
        boolean certificatePublicKeyChanged,
        boolean certificateSerialNumberChanged,
        boolean certificateIssuerChanged,
        SchemaChange schemaChange,
        Duration oldCertificateTimeToExpiry,
        boolean securityHeadersChanged,
        boolean ipChanged,
        boolean softwareChanged,
        JsonObject<DomainSecurityRecord> securitySignatureBefore,
        JsonObject<DomainSecurityRecord> securitySignatureAfter
) implements WritableModel {

    @Override
    public void write(Connection connection) throws SQLException {
        try (var ps = connection.prepareStatement("""
            INSERT INTO DOMAIN_SECURITY_EVENTS (
                domain_id,
                node_id,
                ts_change,
                change_asn,
                change_certificate_fingerprint,
                change_certificate_profile,
                change_certificate_san,
                change_certificate_public_key,
                change_security_headers,
                change_ip_address,
                change_software,
                old_cert_time_to_expiry,
                security_signature_before,
                security_signature_after,
                change_certificate_serial_number,
                change_certificate_issuer,
                change_schema
                ) VALUES    (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """))
        {

            ps.setInt(1, domainId());
            ps.setInt(2, nodeId());
            ps.setTimestamp(3, java.sql.Timestamp.from(tsChange()));
            ps.setBoolean(4, asnChanged());
            ps.setBoolean(5, certificateFingerprintChanged());
            ps.setBoolean(6, certificateProfileChanged());
            ps.setBoolean(7, certificateSanChanged());
            ps.setBoolean(8, certificatePublicKeyChanged());
            ps.setBoolean(9, securityHeadersChanged());
            ps.setBoolean(10, ipChanged());
            ps.setBoolean(11, softwareChanged());

            if (oldCertificateTimeToExpiry() == null) {
                ps.setNull(12, java.sql.Types.BIGINT);
            } else {
                ps.setLong(12, oldCertificateTimeToExpiry().toHours());
            }

            if (securitySignatureBefore() == null) {
                ps.setNull(13, java.sql.Types.BLOB);
            } else {
                ps.setBytes(13, securitySignatureBefore().compressed());
            }
            if (securitySignatureAfter() == null) {
                ps.setNull(14, java.sql.Types.BLOB);
            } else {
                ps.setBytes(14, securitySignatureAfter().compressed());
            }

            ps.setBoolean(15, certificateSerialNumberChanged());
            ps.setBoolean(16, certificateIssuerChanged());
            ps.setString(17, schemaChange.name());

            ps.executeUpdate();
        }
    }
}

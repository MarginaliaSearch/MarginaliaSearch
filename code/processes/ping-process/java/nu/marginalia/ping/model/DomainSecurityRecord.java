package nu.marginalia.ping.model;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Objects;

public record DomainSecurityRecord(
        int domainId,
        int nodeId,
        @Nullable Integer asn,
        @Nullable HttpSchema httpSchema,
        @Nullable String httpVersion,
        @Nullable String httpCompression,
        @Nullable String httpCacheControl,
        @Nullable Instant sslCertNotBefore,
        @Nullable Instant sslCertNotAfter,
        @Nullable String sslCertIssuer,
        @Nullable String sslCertSubject,
        @Nullable byte[] sslCertPublicKeyHash,
        @Nullable String sslCertSerialNumber,
        @Nullable byte[] sslCertFingerprintSha256,
        @Nullable String sslCertSan,
        boolean sslCertWildcard,
        @Nullable String sslProtocol,
        @Nullable String sslCipherSuite,
        @Nullable String sslKeyExchange,
        @Nullable Integer sslCertificateChainLength,
        boolean sslCertificateValid,
        @Nullable String headerCorsAllowOrigin,
        boolean headerCorsAllowCredentials,
        @Nullable Integer headerContentSecurityPolicyHash,
        @Nullable String headerStrictTransportSecurity,
        @Nullable String headerReferrerPolicy,
        @Nullable String headerXFrameOptions,
        @Nullable String headerXContentTypeOptions,
        @Nullable String headerXXssProtection,
        @Nullable String headerServer,
        @Nullable String headerXPoweredBy,
        @Nullable Instant tsLastUpdate
        )
    implements WritableModel
{

    public int certificateProfileHash() {
        return Objects.hash(
                sslCertIssuer,
                sslCertSubject,
                sslCipherSuite,
                sslKeyExchange
        );
    }

    public int securityHeadersHash() {
        return Objects.hash(
                headerCorsAllowOrigin,
                headerCorsAllowCredentials,
                headerContentSecurityPolicyHash,
                headerStrictTransportSecurity,
                headerReferrerPolicy,
                headerXFrameOptions,
                headerXContentTypeOptions,
                headerXXssProtection
        );
    }


    public DomainSecurityRecord(ResultSet rs) throws SQLException {
        this(rs.getInt("DOMAIN_SECURITY_INFORMATION.DOMAIN_ID"),
             rs.getInt("DOMAIN_SECURITY_INFORMATION.NODE_ID"),
             rs.getObject("DOMAIN_SECURITY_INFORMATION.ASN", Integer.class),
             httpSchemaFromString(rs.getString("DOMAIN_SECURITY_INFORMATION.HTTP_SCHEMA")),
             rs.getString("DOMAIN_SECURITY_INFORMATION.HTTP_VERSION"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.HTTP_COMPRESSION"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.HTTP_CACHE_CONTROL"),
             rs.getObject("DOMAIN_SECURITY_INFORMATION.SSL_CERT_NOT_BEFORE", Instant.class),
             rs.getObject("DOMAIN_SECURITY_INFORMATION.SSL_CERT_NOT_AFTER", Instant.class),
             rs.getString("DOMAIN_SECURITY_INFORMATION.SSL_CERT_ISSUER"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.SSL_CERT_SUBJECT"),
             rs.getBytes("DOMAIN_SECURITY_INFORMATION.SSL_CERT_PUBLIC_KEY_HASH"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.SSL_CERT_SERIAL_NUMBER"),
             rs.getBytes("DOMAIN_SECURITY_INFORMATION.SSL_CERT_FINGERPRINT_SHA256"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.SSL_CERT_SAN"),
             rs.getBoolean("DOMAIN_SECURITY_INFORMATION.SSL_CERT_WILDCARD"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.SSL_PROTOCOL"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.SSL_CIPHER_SUITE"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.SSL_KEY_EXCHANGE"),
             rs.getObject("DOMAIN_SECURITY_INFORMATION.SSL_CERTIFICATE_CHAIN_LENGTH", Integer.class),
             rs.getBoolean("DOMAIN_SECURITY_INFORMATION.SSL_CERTIFICATE_VALID"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.HEADER_CORS_ALLOW_ORIGIN"),
             rs.getBoolean("DOMAIN_SECURITY_INFORMATION.HEADER_CORS_ALLOW_CREDENTIALS"),
             rs.getInt("DOMAIN_SECURITY_INFORMATION.HEADER_CONTENT_SECURITY_POLICY_HASH"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.HEADER_STRICT_TRANSPORT_SECURITY"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.HEADER_REFERRER_POLICY"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.HEADER_X_FRAME_OPTIONS"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.HEADER_X_CONTENT_TYPE_OPTIONS"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.HEADER_X_XSS_PROTECTION"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.HEADER_SERVER"),
             rs.getString("DOMAIN_SECURITY_INFORMATION.HEADER_X_POWERED_BY"),
             rs.getObject("DOMAIN_SECURITY_INFORMATION.TS_LAST_UPDATE", Instant.class));
    }

    private static HttpSchema httpSchemaFromString(@Nullable String schema) {
        return schema == null ? null : HttpSchema.valueOf(schema);
    }

    private static SslCertRevocationStatus sslCertRevocationStatusFromString(@Nullable String status) {
        return status == null ? null : SslCertRevocationStatus.valueOf(status);
    }

    @Override
    public void write(Connection connection) throws SQLException {
        try (var ps = connection.prepareStatement(
                     """
                         REPLACE INTO DOMAIN_SECURITY_INFORMATION (
                             domain_id,
                             node_id,
                             http_schema,
                             http_version,
                             http_compression,
                             http_cache_control,
                             ssl_cert_not_before,
                             ssl_cert_not_after,
                             ssl_cert_issuer,
                             ssl_cert_subject,
                             ssl_cert_serial_number,
                             ssl_cert_fingerprint_sha256,
                             ssl_cert_san,
                             ssl_cert_wildcard,
                             ssl_protocol,
                             ssl_cipher_suite,
                             ssl_key_exchange,
                             ssl_certificate_chain_length,
                             ssl_certificate_valid,
                             header_cors_allow_origin,
                             header_cors_allow_credentials,
                             header_content_security_policy_hash,
                             header_strict_transport_security,
                             header_referrer_policy,
                             header_x_frame_options,
                             header_x_content_type_options,
                             header_x_xss_protection,
                             header_server,
                             header_x_powered_by,
                             ssl_cert_public_key_hash,
                             asn,
                             ts_last_update)
                         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """))
        {
            ps.setInt(1, domainId());
            ps.setInt(2, nodeId());
            if (httpSchema() == null) {
                ps.setNull(3, java.sql.Types.VARCHAR);
            } else {
                ps.setString(3, httpSchema().name());
            }
            if (httpVersion() == null) {
                ps.setNull(4, java.sql.Types.VARCHAR);
            } else {
                ps.setString(4, httpVersion());
            }
            if (httpCompression() == null) {
                ps.setNull(5, java.sql.Types.VARCHAR);
            } else {
                ps.setString(5, httpCompression());
            }
            if (httpCacheControl() == null) {
                ps.setNull(6, java.sql.Types.VARCHAR);
            } else {
                ps.setString(6, httpCacheControl());
            }
            if (sslCertNotBefore() == null) {
                ps.setNull(7, java.sql.Types.TIMESTAMP);
            } else {
                ps.setTimestamp(7, java.sql.Timestamp.from(sslCertNotBefore()));
            }
            if (sslCertNotAfter() == null) {
                ps.setNull(8, java.sql.Types.TIMESTAMP);
            } else {
                ps.setTimestamp(8, java.sql.Timestamp.from(sslCertNotAfter()));
            }
            if (sslCertIssuer() == null) {
                ps.setNull(9, java.sql.Types.VARCHAR);
            } else {
                ps.setString(9, sslCertIssuer());
            }
            if (sslCertSubject() == null) {
                ps.setNull(10, java.sql.Types.VARCHAR);
            } else {
                ps.setString(10, sslCertSubject());
            }
            if (sslCertSerialNumber() == null) {
                ps.setNull(11, java.sql.Types.VARCHAR);
            } else {
                ps.setString(11, sslCertSerialNumber());
            }
            if (sslCertFingerprintSha256() == null) {
                ps.setNull(12, java.sql.Types.BINARY);
            } else {
                System.out.println(sslCertFingerprintSha256().length);
                ps.setBytes(12, sslCertFingerprintSha256());
            }
            if (sslCertSan() == null) {
                ps.setNull(13, java.sql.Types.VARCHAR);
            } else {
                ps.setString(13, sslCertSan());
            }
            ps.setBoolean(14, sslCertWildcard());
            if (sslProtocol() == null) {
                ps.setNull(15, java.sql.Types.VARCHAR);
            } else {
                ps.setString(15, sslProtocol());
            }
            if (sslCipherSuite() == null) {
                ps.setNull(16, java.sql.Types.VARCHAR);
            } else {
                ps.setString(16, sslCipherSuite());
            }
            if (sslKeyExchange() == null) {
                ps.setNull(17, java.sql.Types.VARCHAR);
            } else {
                ps.setString(17, sslKeyExchange());
            }
            if (sslCertificateChainLength() == null) {
                ps.setNull(18, java.sql.Types.INTEGER);
            } else {
                ps.setInt(18, sslCertificateChainLength());
            }
            ps.setBoolean(19, sslCertificateValid());
            if (headerCorsAllowOrigin() == null) {
                ps.setNull(20, java.sql.Types.VARCHAR);
            } else {
                ps.setString(20, headerCorsAllowOrigin());
            }
            ps.setBoolean(21, headerCorsAllowCredentials());
            if (headerContentSecurityPolicyHash() == null) {
                ps.setNull(22, Types.INTEGER);
            } else {
                ps.setInt(22, headerContentSecurityPolicyHash());
            }
            if (headerStrictTransportSecurity() == null) {
                ps.setNull(23, java.sql.Types.VARCHAR);
            } else {
                ps.setString(23, headerStrictTransportSecurity());
            }
            if (headerReferrerPolicy() == null) {
                ps.setNull(24, java.sql.Types.VARCHAR);
            } else {
                ps.setString(24, headerReferrerPolicy());
            }
            if (headerXFrameOptions() == null) {
                ps.setNull(25, java.sql.Types.VARCHAR);
            } else {
                ps.setString(25, StringUtils.truncate(headerXFrameOptions(), 50));
            }
            if (headerXContentTypeOptions() == null) {
                ps.setNull(26, java.sql.Types.VARCHAR);
            } else {
                ps.setString(26, headerXContentTypeOptions());
            }
            if (headerXXssProtection() == null) {
                ps.setNull(27, java.sql.Types.VARCHAR);
            } else {
                ps.setString(27, headerXXssProtection());
            }
            if (headerServer() == null) {
                ps.setNull(28, java.sql.Types.VARCHAR);
            } else {
                ps.setString(28, headerServer());
            }
            if (headerXPoweredBy() == null) {
                ps.setNull(29, java.sql.Types.VARCHAR);
            } else {
                ps.setString(29, headerXPoweredBy());
            }
            if (sslCertPublicKeyHash() == null) {
                ps.setNull(30, java.sql.Types.BINARY);
            } else {
                ps.setBytes(30, sslCertPublicKeyHash());
            }
            if (asn() == null) {
                ps.setNull(31, java.sql.Types.INTEGER);
            } else {
                ps.setInt(31, asn());
            }

            if (tsLastUpdate() == null) {
                ps.setNull(32, java.sql.Types.TIMESTAMP);
            } else {
                ps.setTimestamp(32, java.sql.Timestamp.from(tsLastUpdate()));
            }
            ps.executeUpdate();
        }
    }

    public static class Builder {
        private int domainId;
        private int nodeId;
        private Integer asn;
        private HttpSchema httpSchema;
        private String httpVersion;
        private String httpCompression;
        private String httpCacheControl;
        private Instant sslCertNotBefore;
        private Instant sslCertNotAfter;
        private String sslCertIssuer;
        private String sslCertSubject;
        private String sslCertSerialNumber;
        private byte[] sslCertPublicKeyHash;
        private byte[] sslCertFingerprintSha256;
        private String sslCertSan;
        private boolean sslCertWildcard;
        private String sslProtocol;
        private String sslCipherSuite;
        private String sslKeyExchange;
        private Integer sslCertificateChainLength;
        private boolean sslCertificateValid;
        private String headerCorsAllowOrigin;
        private boolean headerCorsAllowCredentials;
        private Integer headerContentSecurityPolicyHash;
        private String headerStrictTransportSecurity;
        private String headerReferrerPolicy;
        private String headerXFrameOptions;
        private String headerXContentTypeOptions;
        private String headerXXssProtection;
        private String headerServer;
        private String headerXPoweredBy;
        private Instant tsLastUpdate;

        public Builder() {
            // Default values for boolean fields
            this.sslCertWildcard = false;
            this.sslCertificateValid = false;
            this.headerCorsAllowCredentials = false;
        }

        public Builder domainId(int domainId) {
            this.domainId = domainId;
            return this;
        }

        public Builder nodeId(int nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder asn(@Nullable Integer asn) {
            this.asn = asn;
            return this;
        }

        public Builder httpSchema(HttpSchema httpSchema) {
            this.httpSchema = httpSchema;
            return this;
        }

        public Builder httpVersion(String httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public Builder httpCompression(String httpCompression) {
            this.httpCompression = httpCompression;
            return this;
        }

        public Builder httpCacheControl(String httpCacheControl) {
            this.httpCacheControl = httpCacheControl;
            return this;
        }

        public Builder sslCertNotBefore(Instant sslCertNotBefore) {
            this.sslCertNotBefore = sslCertNotBefore;
            return this;
        }

        public Builder sslCertNotAfter(Instant sslCertNotAfter) {
            this.sslCertNotAfter = sslCertNotAfter;
            return this;
        }

        public Builder sslCertIssuer(String sslCertIssuer) {
            this.sslCertIssuer = sslCertIssuer;
            return this;
        }

        public Builder sslCertSubject(String sslCertSubject) {
            this.sslCertSubject = sslCertSubject;
            return this;
        }

        public Builder sslCertSerialNumber(String sslCertSerialNumber) {
            this.sslCertSerialNumber = sslCertSerialNumber;
            return this;
        }

        public Builder sslCertPublicKeyHash(byte[] sslCertPublicKeyHash) {
            this.sslCertPublicKeyHash = sslCertPublicKeyHash;
            return this;
        }

        public Builder sslCertFingerprintSha256(byte[] sslCertFingerprintSha256) {
            this.sslCertFingerprintSha256 = sslCertFingerprintSha256;
            return this;
        }

        public Builder sslCertSan(String sslCertSan) {
            this.sslCertSan = sslCertSan;
            return this;
        }

        public Builder sslCertWildcard(boolean sslCertWildcard) {
            this.sslCertWildcard = sslCertWildcard;
            return this;
        }

        public Builder sslProtocol(String sslProtocol) {
            this.sslProtocol = sslProtocol;
            return this;
        }

        public Builder sslCipherSuite(String sslCipherSuite) {
            this.sslCipherSuite = sslCipherSuite;
            return this;
        }

        public Builder sslKeyExchange(String sslKeyExchange) {
            this.sslKeyExchange = sslKeyExchange;
            return this;
        }

        public Builder sslCertificateChainLength(Integer sslCertificateChainLength) {
            this.sslCertificateChainLength = sslCertificateChainLength;
            return this;
        }

        public Builder sslCertificateValid(boolean sslCertificateValid) {
            this.sslCertificateValid = sslCertificateValid;
            return this;
        }

        public Builder headerCorsAllowOrigin(String headerCorsAllowOrigin) {
            this.headerCorsAllowOrigin = headerCorsAllowOrigin;
            return this;
        }

        public Builder headerCorsAllowCredentials(boolean headerCorsAllowCredentials) {
            this.headerCorsAllowCredentials = headerCorsAllowCredentials;
            return this;
        }

        public Builder headerContentSecurityPolicyHash(Integer headerContentSecurityPolicyHash) {
            this.headerContentSecurityPolicyHash = headerContentSecurityPolicyHash;
            return this;
        }

        public Builder headerStrictTransportSecurity(String headerStrictTransportSecurity) {
            this.headerStrictTransportSecurity = headerStrictTransportSecurity;
            return this;
        }

        public Builder headerReferrerPolicy(String headerReferrerPolicy) {
            this.headerReferrerPolicy = headerReferrerPolicy;
            return this;
        }

        public Builder headerXFrameOptions(String headerXFrameOptions) {
            this.headerXFrameOptions = headerXFrameOptions;
            return this;
        }

        public Builder headerXContentTypeOptions(String headerXContentTypeOptions) {
            this.headerXContentTypeOptions = headerXContentTypeOptions;
            return this;
        }

        public Builder headerXXssProtection(String headerXXssProtection) {
            this.headerXXssProtection = headerXXssProtection;
            return this;
        }

        public Builder headerServer(String headerServer) {
            this.headerServer = headerServer;
            return this;
        }

        public Builder headerXPoweredBy(String headerXPoweredBy) {
            this.headerXPoweredBy = headerXPoweredBy;
            return this;
        }

        public Builder tsLastUpdate(Instant tsLastUpdate) {
            this.tsLastUpdate = tsLastUpdate;
            return this;
        }

        public DomainSecurityRecord build() {
            return new DomainSecurityRecord(
                    domainId,
                    nodeId,
                    asn,
                    httpSchema,
                    httpVersion,
                    httpCompression,
                    httpCacheControl,
                    sslCertNotBefore,
                    sslCertNotAfter,
                    sslCertIssuer,
                    sslCertSubject,
                    sslCertPublicKeyHash,
                    sslCertSerialNumber,
                    sslCertFingerprintSha256,
                    sslCertSan,
                    sslCertWildcard,
                    sslProtocol,
                    sslCipherSuite,
                    sslKeyExchange,
                    sslCertificateChainLength,
                    sslCertificateValid,
                    headerCorsAllowOrigin,
                    headerCorsAllowCredentials,
                    headerContentSecurityPolicyHash,
                    headerStrictTransportSecurity,
                    headerReferrerPolicy,
                    headerXFrameOptions,
                    headerXContentTypeOptions,
                    headerXXssProtection,
                    headerServer,
                    headerXPoweredBy,
                    tsLastUpdate
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

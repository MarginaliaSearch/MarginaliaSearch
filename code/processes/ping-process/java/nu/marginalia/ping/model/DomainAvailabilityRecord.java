package nu.marginalia.ping.model;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

public record DomainAvailabilityRecord(
        int domainId,
        int nodeId,
        boolean serverAvailable,
        @Nullable byte[] serverIp,
        @Nullable Integer asn,
        @Nullable Long dataHash,
        @Nullable Long securityConfigHash,
        @Nullable HttpSchema httpSchema,
        @Nullable String httpEtag,
        @Nullable String httpLastModified,
        @Nullable Integer httpStatus,
        @Nullable String httpLocation,
        @Nullable Duration httpResponseTime,
        @Nullable ErrorClassification errorClassification,
        @Nullable String errorMessage,

        @Nullable Instant tsLastPing,
        @Nullable Instant tsLastAvailable,
        @Nullable Instant tsLastError,

        Instant nextScheduledUpdate,
        int backoffConsecutiveFailures,
        Duration backoffFetchInterval
)
implements WritableModel
{
    public DomainAvailabilityRecord(ResultSet rs) throws SQLException {
        this(
                rs.getInt("DOMAIN_AVAILABILITY_INFORMATION.DOMAIN_ID"),
                rs.getInt("DOMAIN_AVAILABILITY_INFORMATION.NODE_ID"),
                rs.getBoolean("DOMAIN_AVAILABILITY_INFORMATION.SERVER_AVAILABLE"),
                rs.getBytes("DOMAIN_AVAILABILITY_INFORMATION.SERVER_IP"),
                rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.SERVER_IP_ASN", Integer.class),
                rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.DATA_HASH", Long.class),
                rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.SECURITY_CONFIG_HASH", Long.class),
                httpSchemaFromString(rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.HTTP_SCHEMA", String.class)),
                rs.getString("DOMAIN_AVAILABILITY_INFORMATION.HTTP_ETAG"),
                rs.getString("DOMAIN_AVAILABILITY_INFORMATION.HTTP_LAST_MODIFIED"),
                rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.HTTP_STATUS", Integer.class),
                rs.getString("DOMAIN_AVAILABILITY_INFORMATION.HTTP_LOCATION"),
                durationFromMillis(rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.HTTP_RESPONSE_TIME_MS", Integer.class)),
                errorClassificationFromString(rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.ERROR_CLASSIFICATION", String.class)),
                rs.getString("DOMAIN_AVAILABILITY_INFORMATION.ERROR_MESSAGE"),
                rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.TS_LAST_PING", Instant.class),
                rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.TS_LAST_AVAILABLE", Instant.class),
                rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.TS_LAST_ERROR", Instant.class),
                rs.getObject("DOMAIN_AVAILABILITY_INFORMATION.NEXT_SCHEDULED_UPDATE", Instant.class),
                rs.getInt("DOMAIN_AVAILABILITY_INFORMATION.BACKOFF_CONSECUTIVE_FAILURES"),
                Duration.ofSeconds(rs.getInt("DOMAIN_AVAILABILITY_INFORMATION.BACKOFF_FETCH_INTERVAL"))
        );
    }

    private static HttpSchema httpSchemaFromString(@Nullable String schema) {
        return schema == null ? null : HttpSchema.valueOf(schema);
    }
    private static ErrorClassification errorClassificationFromString(@Nullable String classification) {
        return classification == null ? null : ErrorClassification.valueOf(classification);
    }
    private static Duration durationFromMillis(@Nullable Integer millis) {
        return millis == null ? null : Duration.ofMillis(millis);
    }

    @Override
    public Instant nextUpdateTime() {
        return nextScheduledUpdate;
    }

    @Override
    public void write(Connection connection) throws SQLException {
        try (var ps = connection.prepareStatement(
                     """
                         REPLACE INTO DOMAIN_AVAILABILITY_INFORMATION (
                                                         domain_id,
                                                         node_id,
                                                         server_available,
                                                         server_ip,
                                                         data_hash,
                                                         security_config_hash,
                                                         http_schema,
                                                         http_etag,
                                                         http_last_modified,
                                                         http_status,
                                                         http_location,
                                                         http_response_time_ms,
                                                         error_classification,
                                                         error_message,
                                                         ts_last_ping,
                                                         ts_last_available,
                                                         ts_last_error,
                                                         next_scheduled_update,
                                                         backoff_consecutive_failures,
                                                         backoff_fetch_interval,
                                                         server_ip_asn)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)
                     """)) {

            ps.setInt(1, domainId());
            ps.setInt(2, nodeId());
            ps.setBoolean(3, serverAvailable());
            if (serverIp() == null) {
                ps.setNull(4, java.sql.Types.BINARY);
            } else {
                ps.setBytes(4, serverIp());
            }
            if (dataHash() == null) {
                ps.setNull(5, java.sql.Types.BIGINT);
            } else {
                ps.setLong(5, dataHash());
            }
            if (securityConfigHash() == null) {
                ps.setNull(6, java.sql.Types.BIGINT);
            } else {
                ps.setLong(6, securityConfigHash());
            }
            if (httpSchema() == null) {
                ps.setNull(7, java.sql.Types.VARCHAR);
            } else {
                ps.setString(7, httpSchema().name());
            }
            if (httpEtag() == null) {
                ps.setNull(8, java.sql.Types.VARCHAR);
            } else {
                ps.setString(8, httpEtag());
            }
            if (httpLastModified() == null) {
                ps.setNull(9, java.sql.Types.VARCHAR);
            } else {
                ps.setString(9, httpLastModified());
            }
            if (httpStatus() == null) {
                ps.setNull(10, java.sql.Types.INTEGER);
            }
            else {
                ps.setInt(10, httpStatus());
            }
            if (httpLocation() == null) {
                ps.setNull(11, java.sql.Types.VARCHAR);
            }
            else {
                ps.setString(11, httpLocation());
            }

            if (httpResponseTime() == null) {
                ps.setNull(12, java.sql.Types.SMALLINT);
            }
            else {
                ps.setInt(12, Math.clamp(httpResponseTime().toMillis(), 0, 0xFFFF)); // "unsigned short" in SQL
            }

            if (errorClassification() == null) {
                ps.setNull(13, java.sql.Types.VARCHAR);
            }
            else {
                ps.setString(13, errorClassification().name());
            }

            if (errorMessage() == null) {
                ps.setNull(14, java.sql.Types.VARCHAR);
            }
            else {
                ps.setString(14, errorMessage());
            }

            ps.setTimestamp(15, java.sql.Timestamp.from(tsLastPing()));

            if (tsLastAvailable() == null) {
                ps.setNull(16, java.sql.Types.TIMESTAMP);
            }
            else {
                ps.setTimestamp(16, java.sql.Timestamp.from(tsLastAvailable()));
            }
            if (tsLastError() == null) {
                ps.setNull(17, java.sql.Types.TIMESTAMP);
            }
            else {
                ps.setTimestamp(17, java.sql.Timestamp.from(tsLastError()));
            }

            ps.setTimestamp(18, java.sql.Timestamp.from(nextScheduledUpdate()));
            ps.setInt(19, backoffConsecutiveFailures());
            ps.setInt(20, (int) backoffFetchInterval().getSeconds());

            if (asn() == null) {
                ps.setNull(21, java.sql.Types.INTEGER);
            } else {
                ps.setInt(21, asn());
            }

            ps.executeUpdate();
        }
    }

    public static class Builder {
        private int domainId;
        private int nodeId;
        private boolean serverAvailable;
        private byte[] serverIp;
        private Integer serverIpAsn;
        private Long dataHash;
        private Long securityConfigHash;
        private HttpSchema httpSchema;
        private String httpEtag;
        private String httpLastModified;
        private Integer httpStatus;
        private String httpLocation;
        private Duration httpResponseTime;
        private ErrorClassification errorClassification;
        private String errorMessage;
        private Instant tsLastPing;
        private Instant tsLastAvailable;
        private Instant tsLastError;
        private Instant nextScheduledUpdate;
        private int backoffConsecutiveFailures;
        private Duration backoffFetchInterval;

        public Builder domainId(int domainId) {
            this.domainId = domainId;
            return this;
        }

        public Builder nodeId(int nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder serverAvailable(boolean serverAvailable) {
            this.serverAvailable = serverAvailable;
            return this;
        }

        public Builder serverIp(byte[] serverIp) {
            this.serverIp = serverIp;
            return this;
        }

        public Builder serverIpAsn(Integer asn) {
            this.serverIpAsn = asn;
            return this;
        }

        public Builder dataHash(Long dataHash) {
            this.dataHash = dataHash;
            return this;
        }

        public Builder securityConfigHash(Long securityConfigHash) {
            this.securityConfigHash = securityConfigHash;
            return this;
        }

        public Builder httpSchema(HttpSchema httpSchema) {
            this.httpSchema = httpSchema;
            return this;
        }

        public Builder httpEtag(String httpEtag) {
            this.httpEtag = httpEtag;
            return this;
        }

        public Builder httpLastModified(String httpLastModified) {
            this.httpLastModified = httpLastModified;
            return this;
        }

        public Builder httpStatus(Integer httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        public Builder httpLocation(String httpLocation) {
            this.httpLocation = StringUtils.abbreviate(httpLocation, "...",255);
            return this;
        }

        public Builder httpResponseTime(Duration httpResponseTime) {
            this.httpResponseTime = httpResponseTime;
            return this;
        }

        public Builder errorClassification(ErrorClassification errorClassification) {
            this.errorClassification = errorClassification;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder tsLastPing(Instant tsLastPing) {
            this.tsLastPing = tsLastPing;
            return this;
        }

        public Builder tsLastAvailable(Instant tsLastAvailable) {
            this.tsLastAvailable = tsLastAvailable;
            return this;
        }

        public Builder tsLastError(Instant tsLastError) {
            this.tsLastError = tsLastError;
            return this;
        }

        public Builder nextScheduledUpdate(Instant nextScheduledUpdate) {
            this.nextScheduledUpdate = nextScheduledUpdate;
            return this;
        }

        public Builder backoffConsecutiveFailures(int backoffConsecutiveFailures) {
            this.backoffConsecutiveFailures = backoffConsecutiveFailures;
            return this;
        }

        public Builder backoffFetchInterval(Duration backoffFetchInterval) {
            this.backoffFetchInterval = backoffFetchInterval;
            return this;
        }

        public DomainAvailabilityRecord build() {
            return new DomainAvailabilityRecord(
                    domainId,
                    nodeId,
                    serverAvailable,
                    serverIp,
                    serverIpAsn,
                    dataHash,
                    securityConfigHash,
                    httpSchema,
                    httpEtag,
                    httpLastModified,
                    httpStatus,
                    httpLocation,
                    httpResponseTime,
                    errorClassification,
                    errorMessage,
                    tsLastPing,
                    tsLastAvailable,
                    tsLastError,
                    nextScheduledUpdate,
                    backoffConsecutiveFailures,
                    backoffFetchInterval
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

package nu.marginalia.functions.domains;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.domains.RpcDomainInfoPingData;
import nu.marginalia.api.domains.RpcDomainInfoResponse;
import nu.marginalia.api.domains.RpcDomainInfoSecurityData;
import nu.marginalia.api.linkgraph.AggregateLinkGraphClient;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class DomainInformationService {
    private final GeoIpDictionary geoIpDictionary;

    private DbDomainQueries dbDomainQueries;
    private final AggregateLinkGraphClient linkGraphClient;
    private HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DomainInformationService(
            DbDomainQueries dbDomainQueries,
            GeoIpDictionary geoIpDictionary,
            AggregateLinkGraphClient linkGraphClient,
            HikariDataSource dataSource) {
        this.dbDomainQueries = dbDomainQueries;
        this.geoIpDictionary = geoIpDictionary;
        this.linkGraphClient = linkGraphClient;
        this.dataSource = dataSource;
    }


    public Optional<RpcDomainInfoResponse> domainInfo(int domainId) {

        Optional<EdgeDomain> domain = dbDomainQueries.getDomain(domainId);
        if (domain.isEmpty()) {
            return Optional.empty();
        }

        try (var connection = dataSource.getConnection();
             var stmt = connection.prepareStatement("""
                SELECT
                    d.IP, d.NODE_AFFINITY, d.DOMAIN_NAME, d.STATE, IFNULL(d.RANK, 1) AS RANK,
                    EXISTS(SELECT 1 FROM CRAWL_QUEUE cq WHERE cq.DOMAIN_NAME = d.DOMAIN_NAME) AS IN_CRAWL_QUEUE,
                    dm.ID AS DM_ID, dm.KNOWN_URLS, dm.GOOD_URLS, dm.VISITED_URLS,
                    dai.DOMAIN_ID AS DAI_DOMAIN_ID,
                    dai.SERVER_AVAILABLE, dai.HTTP_SCHEMA,
                    COALESCE(dai.HTTP_RESPONSE_TIME_MS, -1) AS HTTP_RESPONSE_TIME_MS,
                    dai.ERROR_CLASSIFICATION, dai.ERROR_MESSAGE,
                    dai.TS_LAST_PING, dai.TS_LAST_AVAILABLE, dai.TS_LAST_ERROR,
                    dai.BACKOFF_CONSECUTIVE_FAILURES,
                    dsi.DOMAIN_ID AS DSI_DOMAIN_ID,
                    dsi.HTTP_VERSION, dsi.HTTP_COMPRESSION,
                    dsi.HEADER_SERVER, dsi.HEADER_X_POWERED_BY,
                    dsi.SSL_CERT_SUBJECT, dsi.SSL_CERT_SAN,
                    dsi.SSL_PROTOCOL, dsi.SSL_CIPHER_SUITE, dsi.SSL_KEY_EXCHANGE,
                    dsi.SSL_CERT_WILDCARD, dsi.SSL_CERT_NOT_BEFORE, dsi.SSL_CERT_NOT_AFTER,
                    dsi.SSL_CHAIN_VALID, dsi.SSL_DATE_VALID, dsi.SSL_HOST_VALID
                FROM EC_DOMAIN d
                LEFT JOIN DOMAIN_METADATA dm ON dm.ID = d.ID
                LEFT JOIN DOMAIN_AVAILABILITY_INFORMATION dai ON dai.DOMAIN_ID = d.ID
                LEFT JOIN DOMAIN_SECURITY_INFORMATION dsi ON dsi.DOMAIN_ID = d.ID
                WHERE d.ID = ?
            """)
        ) {
            stmt.setInt(1, domainId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return Optional.empty();
            }

            var builder = RpcDomainInfoResponse.newBuilder();

            // EC_DOMAIN fields
            String ip = rs.getString("IP");
            builder.setIp(Objects.requireNonNullElse(ip, ""));

            geoIpDictionary.getAsnInfo(ip).ifPresent(asnInfo -> {
                builder.setAsn(asnInfo.asn());
                builder.setAsnOrg(asnInfo.org());
                builder.setAsnCountry(asnInfo.country());
            });
            builder.setIpCountry(geoIpDictionary.getCountry(ip));

            builder.setNodeAffinity(rs.getInt("NODE_AFFINITY"));
            builder.setDomain(rs.getString("DOMAIN_NAME"));
            builder.setState(rs.getString("STATE"));
            builder.setRanking(Math.round(100.0 * (1.0 - rs.getDouble("RANK"))));

            boolean inCrawlQueue = rs.getBoolean("IN_CRAWL_QUEUE");
            builder.setInCrawlQueue(inCrawlQueue);

            builder.setIncomingLinks(linkGraphClient.countLinksToDomain(domainId));
            builder.setOutboundLinks(linkGraphClient.countLinksFromDomain(domainId));

            // DOMAIN_METADATA (LEFT JOIN -- null if no matching row)
            int pagesVisited = 0;
            if (rs.getObject("DM_ID") != null) {
                pagesVisited = rs.getInt("VISITED_URLS");

                builder.setPagesKnown(rs.getInt("KNOWN_URLS"));
                builder.setPagesIndexed(rs.getInt("GOOD_URLS"));
                builder.setPagesFetched(pagesVisited);
            }

            // DOMAIN_AVAILABILITY_INFORMATION (LEFT JOIN -- null if no matching row)
            if (rs.getObject("DAI_DOMAIN_ID") != null) {
                var pingBuilder = RpcDomainInfoPingData.newBuilder()
                        .setResponseTimeMs(rs.getInt("HTTP_RESPONSE_TIME_MS"))
                        .setServerAvailable(rs.getBoolean("SERVER_AVAILABLE"))
                        .setConsecutiveFailures(rs.getInt("BACKOFF_CONSECUTIVE_FAILURES"));

                Optional.ofNullable(rs.getString("HTTP_SCHEMA")).ifPresent(pingBuilder::setHttpSchema);
                Optional.ofNullable(rs.getString("ERROR_CLASSIFICATION")).ifPresent(pingBuilder::setErrorClassification);
                Optional.ofNullable(rs.getString("ERROR_MESSAGE")).ifPresent(pingBuilder::setErrorDesc);

                Optional.ofNullable(rs.getTimestamp("TS_LAST_PING"))
                        .map(Timestamp::toInstant)
                        .ifPresent(instant -> pingBuilder.setTsLast(instant.toEpochMilli()));
                Optional.ofNullable(rs.getTimestamp("TS_LAST_AVAILABLE"))
                        .map(Timestamp::toInstant)
                        .ifPresent(instant -> pingBuilder.setTsLastAvailable(instant.toEpochMilli()));
                Optional.ofNullable(rs.getTimestamp("TS_LAST_ERROR"))
                        .map(Timestamp::toInstant)
                        .ifPresent(instant -> pingBuilder.setTsLastError(instant.toEpochMilli()));

                builder.setPingData(pingBuilder);
            }

            // DOMAIN_SECURITY_INFORMATION (LEFT JOIN -- null if no matching row)
            if (rs.getObject("DSI_DOMAIN_ID") != null) {
                var secBuilder = RpcDomainInfoSecurityData.newBuilder();
                secBuilder.setHttpCompression(rs.getBoolean("HTTP_COMPRESSION"));
                secBuilder.setSslCertWildcard(rs.getBoolean("SSL_CERT_WILDCARD"));
                secBuilder.setSslChainValid(rs.getBoolean("SSL_CHAIN_VALID"));
                secBuilder.setSslChainDateValid(rs.getBoolean("SSL_DATE_VALID"));
                secBuilder.setSslChainHostValid(rs.getBoolean("SSL_HOST_VALID"));

                Optional.ofNullable(rs.getString("HTTP_VERSION")).ifPresent(secBuilder::setHttpVersion);
                Optional.ofNullable(rs.getString("HEADER_SERVER")).ifPresent(secBuilder::setHeaderServer);
                Optional.ofNullable(rs.getString("HEADER_X_POWERED_BY")).ifPresent(secBuilder::setHeaderPoweredBy);
                Optional.ofNullable(rs.getString("SSL_CERT_SUBJECT")).ifPresent(secBuilder::setSslCertSubject);
                Optional.ofNullable(rs.getString("SSL_CERT_SAN")).ifPresent(secBuilder::setSslCertSAN);
                Optional.ofNullable(rs.getString("SSL_PROTOCOL")).ifPresent(secBuilder::setSslProtocol);
                Optional.ofNullable(rs.getString("SSL_KEY_EXCHANGE")).ifPresent(secBuilder::setSslKeyExchange);
                Optional.ofNullable(rs.getString("SSL_CIPHER_SUITE")).ifPresent(secBuilder::setSslCipherSuite);

                Optional.ofNullable(rs.getTimestamp("SSL_CERT_NOT_BEFORE"))
                        .map(Timestamp::toInstant)
                        .ifPresent(instant -> secBuilder.setSslCertNotBefore(instant.toEpochMilli()));
                Optional.ofNullable(rs.getTimestamp("SSL_CERT_NOT_AFTER"))
                        .map(Timestamp::toInstant)
                        .ifPresent(instant -> secBuilder.setSslCertNotAfter(instant.toEpochMilli()));

                builder.setSecurityData(secBuilder);
            }

            builder.setSuggestForCrawling(pagesVisited == 0 && !inCrawlQueue);

            return Optional.of(builder.build());
        }
        catch (SQLException ex) {
            logger.error("SQL error", ex);
            return Optional.empty();
        }
    }

}

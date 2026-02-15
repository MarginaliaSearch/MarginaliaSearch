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
import org.apache.logging.log4j.core.time.Instant;
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


        var builder = RpcDomainInfoResponse.newBuilder();
        try (var connection = dataSource.getConnection();
             var stmt = connection.createStatement()
        ) {
            boolean inCrawlQueue;
            int outboundLinks = 0;
            int pagesVisited = 0;

            ResultSet rs;

            rs = stmt.executeQuery("SELECT IP, NODE_AFFINITY, DOMAIN_NAME, STATE, IFNULL(RANK, 1) AS RANK\n       FROM EC_DOMAIN WHERE ID=" + domainId + "\n   ");
            if (rs.next()) {
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
                builder.setRanking(Math.round(100.0*(1.0-rs.getDouble("RANK"))));
            }
            rs = stmt.executeQuery("SELECT 1 FROM CRAWL_QUEUE\nINNER JOIN EC_DOMAIN ON CRAWL_QUEUE.DOMAIN_NAME = EC_DOMAIN.DOMAIN_NAME\nWHERE EC_DOMAIN.ID=" + domainId + "\n   ");
            inCrawlQueue = rs.next();
            builder.setInCrawlQueue(inCrawlQueue);

            builder.setIncomingLinks(linkGraphClient.countLinksToDomain(domainId));
            builder.setOutboundLinks(linkGraphClient.countLinksFromDomain(domainId));

            rs = stmt.executeQuery("SELECT KNOWN_URLS, GOOD_URLS, VISITED_URLS FROM DOMAIN_METADATA WHERE ID=" + domainId + "\n   ");
            if (rs.next()) {
                pagesVisited = rs.getInt("VISITED_URLS");

                builder.setPagesKnown(rs.getInt("KNOWN_URLS"));
                builder.setPagesIndexed(rs.getInt("GOOD_URLS"));
                builder.setPagesFetched(rs.getInt("VISITED_URLS"));
            }

            rs = stmt.executeQuery("""
                SELECT SERVER_AVAILABLE,
                       HTTP_SCHEMA,
                       COALESCE(HTTP_RESPONSE_TIME_MS, -1) 
                            AS HTTP_RESPONSE_TIME_MS,
                       ERROR_CLASSIFICATION,
                       ERROR_MESSAGE,
                       TS_LAST_PING,
                       TS_LAST_AVAILABLE,
                       TS_LAST_ERROR,
                       BACKOFF_CONSECUTIVE_FAILURES
                FROM DOMAIN_AVAILABILITY_INFORMATION
                WHERE DOMAIN_ID=
                """ + domainId);

            if (rs.next()) {
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

            rs = stmt.executeQuery("""
                SELECT 
                    HTTP_VERSION, 
                    HTTP_COMPRESSION,
                    HEADER_SERVER, 
                    HEADER_X_POWERED_BY,
                    SSL_CERT_SUBJECT, 
                    SSL_CERT_SAN, 
                    SSL_PROTOCOL, 
                    SSL_CIPHER_SUITE, 
                    SSL_KEY_EXCHANGE, 
                    SSL_CERT_WILDCARD,
                    SSL_CERT_NOT_BEFORE,
                    SSL_CERT_NOT_AFTER, 
                    SSL_CHAIN_VALID, 
                    SSL_DATE_VALID, 
                    SSL_HOST_VALID
                FROM DOMAIN_SECURITY_INFORMATION
                WHERE DOMAIN_ID=
                """ + domainId);
            if (rs.next()) {
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

            builder.setSuggestForCrawling((pagesVisited == 0 && outboundLinks == 0 && !inCrawlQueue));

            return Optional.of(builder.build());
        }
        catch (SQLException ex) {
            logger.error("SQL error", ex);
            return Optional.empty();
        }
    }

}

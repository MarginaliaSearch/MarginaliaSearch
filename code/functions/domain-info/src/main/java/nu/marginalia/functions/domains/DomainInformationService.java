package nu.marginalia.functions.domains;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.domains.RpcDomainInfoResponse;
import nu.marginalia.api.indexdomainlinks.AggregateDomainLinksClient;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.db.DbDomainQueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Singleton
public class DomainInformationService {
    private final GeoIpDictionary geoIpDictionary;

    private DbDomainQueries dbDomainQueries;
    private final AggregateDomainLinksClient domainLinksClient;
    private HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DomainInformationService(
            DbDomainQueries dbDomainQueries,
            GeoIpDictionary geoIpDictionary,
            AggregateDomainLinksClient domainLinksClient,
            HikariDataSource dataSource) {
        this.dbDomainQueries = dbDomainQueries;
        this.geoIpDictionary = geoIpDictionary;
        this.domainLinksClient = domainLinksClient;
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

            rs = stmt.executeQuery(STR."""
                    SELECT IP, NODE_AFFINITY, DOMAIN_NAME, STATE, IFNULL(RANK, 1) AS RANK
                           FROM EC_DOMAIN WHERE ID=\{domainId}
                       """);
            if (rs.next()) {
                String ip = rs.getString("IP");

                builder.setIp(ip);
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
            rs = stmt.executeQuery(STR."""
                    SELECT 1 FROM CRAWL_QUEUE
                    INNER JOIN EC_DOMAIN ON CRAWL_QUEUE.DOMAIN_NAME = EC_DOMAIN.DOMAIN_NAME
                    WHERE EC_DOMAIN.ID=\{domainId}
                       """);
            inCrawlQueue = rs.next();
            builder.setInCrawlQueue(inCrawlQueue);

            builder.setIncomingLinks(domainLinksClient.countLinksToDomain(domainId));
            builder.setOutboundLinks(domainLinksClient.countLinksFromDomain(domainId));

            rs = stmt.executeQuery(STR."""
                    SELECT KNOWN_URLS, GOOD_URLS, VISITED_URLS FROM DOMAIN_METADATA WHERE ID=\{domainId}
                       """);
            if (rs.next()) {
                pagesVisited = rs.getInt("VISITED_URLS");

                builder.setPagesKnown(rs.getInt("KNOWN_URLS"));
                builder.setPagesIndexed(rs.getInt("GOOD_URLS"));
                builder.setPagesFetched(rs.getInt("VISITED_URLS"));
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

package nu.marginalia.search.siteinfo;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.search.model.DomainInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.SQLException;
import java.util.*;

/*
  TODO: This class needs to be refactored, a lot of
        these SQL queries are redundant and can be
        collapsed into one single query that fetches
        all the information
 */
@Singleton
public class DomainInformationService {

    private DbDomainQueries dbDomainQueries;
    private HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DomainInformationService(
            DbDomainQueries dbDomainQueries,
            HikariDataSource dataSource) {
        this.dbDomainQueries = dbDomainQueries;
        this.dataSource = dataSource;
    }


    public Optional<DomainInformation> domainInfo(String site) {

        OptionalInt maybeDomainId = getDomainFromPartial(site);
        if (maybeDomainId.isEmpty()) {
            return Optional.empty();
        }
        int domainId = maybeDomainId.getAsInt();

        Optional<EdgeDomain> domain = dbDomainQueries.getDomain(domainId);
        if (domain.isEmpty()) {
            return Optional.empty();
        }

        boolean blacklisted = isBlacklisted(domain.get());
        int pagesKnown = getPagesKnown(domainId);
        int pagesVisited = getPagesVisited(domainId);
        int pagesIndexed = getPagesIndexed(domainId);
        int incomingLinks = getIncomingLinks(domainId);
        int outboundLinks = getOutboundLinks(domainId);

        boolean inCrawlQueue = inCrawlQueue(domainId);

        double rank = Math.round(10000.0*(1.0-getRank(domainId)))/100;

        DomainIndexingState state = getDomainState(domainId);
        List<EdgeDomain> linkingDomains = getLinkingDomains(domainId);

        var di = DomainInformation.builder()
                .domain(domain.get())
                .blacklisted(blacklisted)
                .pagesKnown(pagesKnown)
                .pagesFetched(pagesVisited)
                .pagesIndexed(pagesIndexed)
                .incomingLinks(incomingLinks)
                .outboundLinks(outboundLinks)
                .ranking(rank)
                .state(state.desc)
                .linkingDomains(linkingDomains)
                .inCrawlQueue(inCrawlQueue)
                .suggestForCrawling((pagesVisited == 0 && !inCrawlQueue))
                .build();

        return Optional.of(di);
    }

    @SneakyThrows
    private boolean inCrawlQueue(int domainId) {
        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement(
                """
                    SELECT 1 FROM CRAWL_QUEUE
                    INNER JOIN EC_DOMAIN ON CRAWL_QUEUE.DOMAIN_NAME = EC_DOMAIN.DOMAIN_NAME
                    WHERE EC_DOMAIN.ID=?
                    """))
            {
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                return rsp.next();
            }
        }
    }

    private OptionalInt getDomainFromPartial(String site) {
        return dbDomainQueries.tryGetDomainId(new EdgeDomain(site));
    }

    @SneakyThrows
    public boolean isBlacklisted(EdgeDomain domain) {

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN_BLACKLIST WHERE URL_DOMAIN IN (?,?)")) {
                stmt.setString(1, domain.domain);
                stmt.setString(2, domain.toString());
                var rsp = stmt.executeQuery();
                return rsp.next();
            }
        }
    }

    @SneakyThrows
    public int getPagesKnown(int domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT KNOWN_URLS FROM DOMAIN_METADATA WHERE ID=?")) {
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }

    @SneakyThrows
    public int getPagesVisited(int domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT VISITED_URLS FROM DOMAIN_METADATA WHERE ID=?")) {
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }


    @SneakyThrows
    public int getPagesIndexed(int domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT GOOD_URLS FROM DOMAIN_METADATA WHERE ID=?")) {
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }

    @SneakyThrows
    public int getIncomingLinks(int domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT COUNT(ID) FROM EC_DOMAIN_LINK WHERE DEST_DOMAIN_ID=?")) {
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }
    @SneakyThrows
    public int getOutboundLinks(int domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT COUNT(ID) FROM EC_DOMAIN_LINK WHERE SOURCE_DOMAIN_ID=?")) {
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }

    @SneakyThrows
    public double getDomainQuality(int domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT QUALITY FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getDouble(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return -5;
        }
    }

    public DomainIndexingState getDomainState(int domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT STATE FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return DomainIndexingState.valueOf(rsp.getString(1));
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return DomainIndexingState.ERROR;
    }

    public List<EdgeDomain> getLinkingDomains(int domainId) {
        try (var connection = dataSource.getConnection()) {
            List<EdgeDomain> results = new ArrayList<>(25);
            try (var stmt = connection.prepareStatement("SELECT SOURCE_DOMAIN FROM EC_RELATED_LINKS_VIEW WHERE DEST_DOMAIN_ID=? ORDER BY SOURCE_DOMAIN_ID LIMIT 25")) {
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    results.add(new EdgeDomain(rsp.getString(1)));
                }
                return results;
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return Collections.emptyList();
    }

    public double getRank(int domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT IFNULL(RANK, 1) FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getDouble(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return 1;
    }
}

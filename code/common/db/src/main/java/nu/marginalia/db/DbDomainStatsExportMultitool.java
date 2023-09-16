package nu.marginalia.db;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Class used in exporting data.  This is intended to be used for a brief time
 * and then discarded, not kept around as a service.
 */
public class DbDomainStatsExportMultitool implements AutoCloseable {
    private final Connection connection;
    private final PreparedStatement knownUrlsQuery;
    private final PreparedStatement visitedUrlsQuery;
    private final PreparedStatement goodUrlsQuery;
    private final PreparedStatement domainNameToId;

    private final PreparedStatement allDomainsQuery;
    private final PreparedStatement crawlQueueDomains;
    private final PreparedStatement indexedDomainsQuery;

    public DbDomainStatsExportMultitool(HikariDataSource dataSource) throws SQLException {
        this.connection = dataSource.getConnection();

        knownUrlsQuery = connection.prepareStatement("""
                SELECT KNOWN_URLS
                FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA
                    ON EC_DOMAIN.ID=DOMAIN_METADATA.ID
                WHERE DOMAIN_NAME=?
                """);
        visitedUrlsQuery = connection.prepareStatement("""
                SELECT VISITED_URLS
                FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA
                    ON EC_DOMAIN.ID=DOMAIN_METADATA.ID
                WHERE DOMAIN_NAME=?
                """);
        goodUrlsQuery = connection.prepareStatement("""
                SELECT GOOD_URLS
                FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA
                    ON EC_DOMAIN.ID=DOMAIN_METADATA.ID
                WHERE DOMAIN_NAME=?
                """);
        domainNameToId = connection.prepareStatement("""
                SELECT ID
                FROM EC_DOMAIN
                WHERE DOMAIN_NAME=?
                """);
        allDomainsQuery = connection.prepareStatement("""
                SELECT DOMAIN_NAME
                FROM EC_DOMAIN
                """);
        crawlQueueDomains = connection.prepareStatement("""
                SELECT DOMAIN_NAME
                FROM CRAWL_QUEUE
                """);
        indexedDomainsQuery = connection.prepareStatement("""
                SELECT DOMAIN_NAME
                FROM EC_DOMAIN
                WHERE INDEXED > 0
                """);
    }

    public OptionalInt getKnownUrls(String domainName) throws SQLException  {
        return executeNameToIntQuery(domainName, knownUrlsQuery);
    }
    public OptionalInt getVisitedUrls(String domainName) throws SQLException {
        return executeNameToIntQuery(domainName, visitedUrlsQuery);
    }
    public OptionalInt getGoodUrls(String domainName) throws SQLException {
        return executeNameToIntQuery(domainName, goodUrlsQuery);
    }
    public OptionalInt getDomainId(String domainName) throws SQLException {
        return executeNameToIntQuery(domainName, domainNameToId);
    }
    public List<String> getAllDomains() throws SQLException {
        return executeListQuery(allDomainsQuery, 100_000);
    }
    public List<String> getCrawlQueueDomains() throws SQLException {
        return executeListQuery(crawlQueueDomains, 100);
    }
    public List<String> getAllIndexedDomains() throws SQLException {
        return executeListQuery(indexedDomainsQuery, 100_000);
    }

    private OptionalInt executeNameToIntQuery(String domainName, PreparedStatement statement)
            throws SQLException {
        statement.setString(1, domainName);
        var rs = statement.executeQuery();

        if (rs.next()) {
            return OptionalInt.of(rs.getInt(1));
        }

        return OptionalInt.empty();
    }

    private List<String> executeListQuery(PreparedStatement statement, int sizeHint) throws SQLException {
        List<String> ret = new ArrayList<>(sizeHint);

        var rs = statement.executeQuery();

        while (rs.next()) {
            ret.add(rs.getString(1));
        }

        return ret;
    }

    @Override
    public void close() throws SQLException {
        knownUrlsQuery.close();
        goodUrlsQuery.close();
        visitedUrlsQuery.close();
        allDomainsQuery.close();
        crawlQueueDomains.close();
        domainNameToId.close();
        connection.close();
    }
}

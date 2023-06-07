package nu.marginalia.crawl;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.db.DomainBlacklistImpl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class CrawlJobDomainExtractor {
    private static final int MIN_VISIT_COUNT = 200;
    private static final int MAX_VISIT_COUNT = 100000;

    private static final String specificDomainSql =
            """
                SELECT ID
                FROM EC_DOMAIN
                WHERE DOMAIN_NAME=?
            """;

    private static final String domainsSql =
            """
               SELECT ID, LOWER(EC_DOMAIN.DOMAIN_NAME)
               FROM EC_DOMAIN
               WHERE INDEXED>0
               AND STATE='ACTIVE' OR STATE='EXHAUSTED'
               ORDER BY
                    INDEX_DATE ASC,
                    DISCOVER_DATE ASC,
                    STATE DESC,
                    INDEXED DESC,
                    EC_DOMAIN.ID
            """;
    private static final String queuedDomainsSql =
            """
               SELECT IFNULL(ID, -1), LOWER(CRAWL_QUEUE.DOMAIN_NAME)
               FROM CRAWL_QUEUE
               LEFT JOIN EC_DOMAIN
               ON CRAWL_QUEUE.DOMAIN_NAME=EC_DOMAIN.DOMAIN_NAME
            """;
    private static final String urlsSql =
            """
                SELECT URL
                FROM EC_URL_VIEW
                WHERE DOMAIN_ID=?
                ORDER BY
                    VISITED DESC,
                    DATA_HASH IS NOT NULL DESC,
                    ID
                LIMIT 25000
            """;

    private static final String visitedUrlsSql =
            """
                SELECT COUNT(*)
                FROM EC_URL
                WHERE DOMAIN_ID=?
                AND VISITED
                ;
            """;


    private final DomainBlacklistImpl blacklist;
    private final HikariDataSource dataSource;
    private static final HashFunction hasher = Hashing.murmur3_128(0);

    public CrawlJobDomainExtractor(DomainBlacklistImpl blacklist, HikariDataSource dataSource) {
        this.blacklist = blacklist;
        this.dataSource = dataSource;
    }

    public Stream<CrawlingSpecification> extractDomainsFromQueue() {
        Set<DomainWithId> ids = new HashSet<>(1_000_000);

        try (var conn = dataSource.getConnection();
             var stmtDomains = conn.prepareStatement(domainsSql);
             var stmtQueue = conn.prepareStatement(queuedDomainsSql);
        ) {
            ResultSet rsp;

            stmtDomains.setFetchSize(10_000);
            rsp = stmtDomains.executeQuery();
            while (rsp.next()) {
                ids.add(new DomainWithId(rsp.getString(2), rsp.getInt(1)));
            }

            stmtQueue.setFetchSize(10_000);
            rsp = stmtQueue.executeQuery();
            while (rsp.next()) {
                ids.add(new DomainWithId(rsp.getString(2), rsp.getInt(1)));
            }
        }
        catch (SQLException ex) {
            ex.printStackTrace();
        }

        return ids.stream()
                .filter(id -> !blacklist.isBlacklisted(id.id))
                .map(this::createCrawlJobForDomain);
    }

    public CrawlingSpecification extractDomain(EdgeDomain domain) {
        CrawlingSpecification spec = new CrawlingSpecification();

        spec.domain = domain.toString();
        spec.id = createId(domain);
        spec.urls = new ArrayList<>(1000);

        try (var conn = dataSource.getConnection();
             var domainQuery = conn.prepareStatement(specificDomainSql);
             var urlQuery = conn.prepareStatement(urlsSql))
        {
            domainQuery.setString(1, domain.toString());
            ResultSet rsp = domainQuery.executeQuery();
            int domainId = rsp.next() ? rsp.getInt(1) : -1;

            spec.crawlDepth = getCrawlDepth(new DomainWithId(domain.toString(), domainId));

            urlQuery.setString(1, domain.toString());
            urlQuery.setInt(2, domainId);
            urlQuery.setFetchSize(1000);
            rsp = urlQuery.executeQuery();

            while (rsp.next()) {
                spec.urls.add(rsp.getString(1));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (spec.urls.isEmpty()) {
            spec.urls.add("https://"+domain+"/");
        }

        return spec;
    }
    private record DomainWithId(String domainName, int id) {


    }

    private CrawlingSpecification createCrawlJobForDomain(DomainWithId domainWithId) {
        var spec = new CrawlingSpecification();
        spec.id = createId(domainWithId);
        spec.domain = domainWithId.domainName;
        spec.urls = new ArrayList<>();
        spec.crawlDepth = getCrawlDepth(domainWithId);

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(urlsSql)) {
            stmt.setFetchSize(1000);
            stmt.setInt(1, domainWithId.id);
            var rsp = stmt.executeQuery();

            while (rsp.next()) {
                spec.urls.add(rsp.getString(1));
            }
        }
        catch (SQLException ex) {
            ex.printStackTrace();
        }

        spec.urls.sort(Comparator.naturalOrder());

        return spec;
    }

    private static String createId(DomainWithId domainWithId) {
        return hasher.hashUnencodedChars(domainWithId.domainName).toString();
    }

    private static String createId(EdgeDomain domain) {
        return hasher.hashUnencodedChars(domain.toString()).toString();
    }

    private int getCrawlDepth(DomainWithId domainWithId) {
        try (var conn = dataSource.getConnection();
             var domainQuery = conn.prepareStatement(visitedUrlsSql)) {
            domainQuery.setInt(1, domainWithId.id);
            var rsp = domainQuery.executeQuery();
            if (rsp.next()) {
                return calculateCrawlDepthFromVisitedCount(rsp.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return MIN_VISIT_COUNT;
    }

    private int calculateCrawlDepthFromVisitedCount(int count) {
        if (count < MIN_VISIT_COUNT / 2) {
            /* If we aren't getting very many good documents
              out of this webpage on previous attempts, we
              won't dig very deeply this time.  This saves time
              and resources for both the crawler and the server,
              and also prevents deep crawls on foreign websites we aren't
              interested in crawling at this point. */
            count = MIN_VISIT_COUNT;
        }
        else {
            /* If we got many results previously, we'll
               dig deeper with each successive crawl. */
            count = count + 1000 + count / 4;
        }

        if (count > MAX_VISIT_COUNT) {
            count = MAX_VISIT_COUNT;
        }

        return count;
    }

}

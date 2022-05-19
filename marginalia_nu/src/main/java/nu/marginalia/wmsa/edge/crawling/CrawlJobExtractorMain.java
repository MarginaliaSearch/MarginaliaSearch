package nu.marginalia.wmsa.edge.crawling;

import com.github.luben.zstd.ZstdOutputStream;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.crawling.model.CrawlingSpecification;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklistImpl;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.mariadb.jdbc.Driver;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;

public class CrawlJobExtractorMain {

    private static final String specificDomainSql =
            """
                SELECT ID
                FROM EC_DOMAIN
                WHERE URL_PART=?
            """;

    private static final String domainsSql =
            """
               SELECT ID, LOWER(EC_DOMAIN.URL_PART)
               FROM EC_DOMAIN
               WHERE QUALITY_RAW>-100
               AND INDEXED>0
               AND STATE<2
               ORDER BY
                    INDEX_DATE ASC,
                    DISCOVER_DATE ASC,
                    STATE DESC,
                    INDEXED DESC,
                    EC_DOMAIN.ID
            """;

    private static final String urlsSql =
            """
                SELECT CONCAT(PROTO, "://", ?, URL)
                FROM EC_URL
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
    private static final int MIN_VISIT_COUNT = 100;
    private static final int MAX_VISIT_COUNT = 5000;

    private final EdgeDomainBlacklistImpl blacklist;

    private final Connection conn;
    private final HashFunction hasher = Hashing.murmur3_128(0);

    public static void main(String... args) throws SQLException, IOException {
        Driver driver = new Driver();
        var outFile = Path.of(args[0]);

        Gson gson = new GsonBuilder().create();
        String[] targetDomains = Arrays.stream(args).skip(1).toArray(String[]::new);


        try (var out = new PrintWriter(new ZstdOutputStream(new BufferedOutputStream(new FileOutputStream(outFile.toFile()))))) {
            final var extractor = new CrawlJobExtractorMain(new DatabaseModule().provideConnection());
            final Stream<CrawlingSpecification> jobs;

            if (targetDomains.length > 0) {
                jobs = Arrays.stream(targetDomains).map(EdgeDomain::new).map(extractor::extractDomain);
            } else {
                jobs = extractor.extractDomains();
            }

            jobs.map(gson::toJson).forEach(out::println);
        }
    }

    private record DomainWithId(String domainName, int id) {};

    private Stream<CrawlingSpecification> extractDomains() {
        List<DomainWithId> ids = new ArrayList<>(100_000);

        try (var stmt = conn.prepareStatement(domainsSql)) {
            stmt.setFetchSize(10_000);
            var rsp = stmt.executeQuery();
            while (rsp.next()) {
                ids.add(new DomainWithId(rsp.getString(2), rsp.getInt(1)));
            }
        }
        catch (SQLException ex) {
            ex.printStackTrace();
        }

        Collections.shuffle(ids);
        return ids.stream()
                .filter(id -> !blacklist.isBlacklisted(id.id))
                .map(this::createCrawlJobForDomain);
    }

    private CrawlingSpecification createCrawlJobForDomain(DomainWithId domainWithId) {
        var spec = new CrawlingSpecification();
        spec.id = createId(domainWithId);
        spec.domain = domainWithId.domainName;
        spec.urls = new ArrayList<>();
        spec.crawlDepth = getCrawlDepth(domainWithId);

        try (var stmt = conn.prepareStatement(urlsSql)) {
            stmt.setFetchSize(1000);
            stmt.setString(1, domainWithId.domainName);
            stmt.setInt(2, domainWithId.id);
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

    public CrawlJobExtractorMain(HikariDataSource ds) throws SQLException {
        blacklist = new EdgeDomainBlacklistImpl(ds);
        conn = ds.getConnection();
    }

    public CrawlingSpecification extractDomain(EdgeDomain domain) {
        CrawlingSpecification spec = new CrawlingSpecification();
        spec.domain = domain.toString();
        spec.id = createId(domain);
        spec.urls = new ArrayList<>(1000);


        try (var domainQuery = conn.prepareStatement(specificDomainSql);
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

    private String createId(DomainWithId domainWithId) {
        return hasher.hashUnencodedChars(domainWithId.domainName).toString();
    }

    private String createId(EdgeDomain domain) {
        return hasher.hashUnencodedChars(domain.toString()).toString();
    }

    private int getCrawlDepth(DomainWithId domainWithId) {
        try (var domainQuery = conn.prepareStatement(visitedUrlsSql)) {
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
        count = count + 100 + count / 4;

        if (count < MIN_VISIT_COUNT) {
            count = MIN_VISIT_COUNT;
        }

        if (count > MAX_VISIT_COUNT) {
            count = MAX_VISIT_COUNT;
        }

        return count;
    }
}

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
import nu.marginalia.util.ranking.BetterReversePageRank;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
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

public class CrawlJobExtractorPageRankMain {

    private static final String specificDomainSql =
            """
                SELECT ID
                FROM EC_DOMAIN
                WHERE URL_PART=?
            """;
    private static final String specificDomainSqlFromId =
            """
                SELECT LOWER(URL_PART)
                FROM EC_DOMAIN
                WHERE ID=?
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

        var rpr = new BetterReversePageRank(new DatabaseModule().provideConnection(),  "bikobatanari.art", "sadgrl.online", "wiki.xxiivv.com", "%neocities.org");
        rpr.setMaxKnownUrls(750);

        var targetDomainIds = rpr.pageRankWithPeripheralNodes(rpr.size(), false);

        try (var out = new PrintWriter(new ZstdOutputStream(new BufferedOutputStream(new FileOutputStream(outFile.toFile()))))) {
            final var extractor = new CrawlJobExtractorPageRankMain(new DatabaseModule().provideConnection());

            targetDomainIds.forEach(i -> {
                out.println(gson.toJson(extractor.extractDomain(new EdgeId<>(i))));
                return true;
            });
        }
    }

    private record DomainWithId(String domainName, int id) {}

    public CrawlJobExtractorPageRankMain(HikariDataSource ds) throws SQLException {
        blacklist = new EdgeDomainBlacklistImpl(ds);
        conn = ds.getConnection();
    }

    public CrawlingSpecification extractDomain(EdgeId<EdgeDomain> domainId) {
        CrawlingSpecification spec = new CrawlingSpecification();

        String domainName = "";
        try (var domainQuery = conn.prepareStatement(specificDomainSqlFromId);
             var urlQuery = conn.prepareStatement(urlsSql))
        {
            domainQuery.setInt(1, domainId.getId());
            ResultSet rsp = domainQuery.executeQuery();
            domainName = rsp.next() ? rsp.getString(1) : "";

            spec.domain = domainName;
            spec.id = createId(new EdgeDomain(domainName));
            spec.urls = new ArrayList<>(1000);

            spec.crawlDepth = getCrawlDepth(new DomainWithId(domainName, domainId.getId()));

            urlQuery.setString(1, domainName.toString());
            urlQuery.setInt(2, domainId.getId());
            urlQuery.setFetchSize(1000);
            rsp = urlQuery.executeQuery();

            while (rsp.next()) {
                spec.urls.add(rsp.getString(1));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (spec.urls.isEmpty()) {
            spec.urls.add("https://"+domainName+"/");
        }

        return spec;
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

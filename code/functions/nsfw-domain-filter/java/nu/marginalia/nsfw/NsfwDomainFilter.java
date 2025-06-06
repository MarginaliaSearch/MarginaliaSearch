package nu.marginalia.nsfw;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

@Singleton
public class NsfwDomainFilter {
    private final HikariDataSource dataSource;

    private final List<String> dangerLists;
    private final List<String> smutLists;

    private volatile IntOpenHashSet blockedDomainIdsTier1 = new IntOpenHashSet();
    private volatile IntOpenHashSet blockedDomainIdsTier2 = new IntOpenHashSet();

    private static final Logger logger = LoggerFactory.getLogger(NsfwDomainFilter.class);

    public static final int NSFW_DISABLE = 0;
    public static final int NSFW_BLOCK_DANGER = 1;
    public static final int NSFW_BLOCK_SMUT = 2;

    @Inject
    public NsfwDomainFilter(HikariDataSource dataSource,
                            @Named("nsfw.dangerLists") List<String> dangerLists,
                            @Named("nsfw.smutLists") List<String> smutLists
                            ) {
        this.dataSource = dataSource;

        this.dangerLists = dangerLists;
        this.smutLists = smutLists;

        Thread.ofPlatform().daemon().name("NsfwDomainFilterSync").start(() -> {
            while (true) {
                sync();
                try {
                    TimeUnit.HOURS.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break; // Exit the loop if interrupted
                }
            }
        });
    }

    public boolean isBlocked(int domainId, int tier) {
        if (tier == 0)
            return false;

        if (tier >= 1 && blockedDomainIdsTier1.contains(domainId))
            return true;
        if (tier >= 2 && blockedDomainIdsTier2.contains(domainId))
            return true;

        return false;
    }

    private synchronized void sync() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT ID, TIER FROM NSFW_DOMAINS")
        ) {
            var rs = stmt.executeQuery();
            IntOpenHashSet tier1 = new IntOpenHashSet();
            IntOpenHashSet tier2 = new IntOpenHashSet();

            while (rs.next()) {
                int domainId = rs.getInt("ID");
                int tier = rs.getInt("TIER");

                switch (tier) {
                    case 1 -> tier1.add(domainId);
                    case 2 -> tier2.add(domainId);
                }
            }

            this.blockedDomainIdsTier1 = tier1;
            this.blockedDomainIdsTier2 = tier2;

            logger.info("NSFW domain filter synced: {} tier 1, {} tier 2", tier1.size(), tier2.size());

        }
        catch (SQLException ex) {
            logger.error("Failed to sync NSFW domain filter", ex);
        }
    }

    public synchronized void fetchLists() {
        try (var conn = dataSource.getConnection();
             HttpClient client = HttpClient.newBuilder()
                     .followRedirects(HttpClient.Redirect.ALWAYS)
                     .build();
             var stmt = conn.createStatement();
             var insertStmt = conn.prepareStatement("INSERT INTO NSFW_DOMAINS_TMP (ID, TIER) SELECT ID, ? FROM EC_DOMAIN WHERE DOMAIN_NAME = ?")) {

            stmt.execute("DROP TABLE IF EXISTS NSFW_DOMAINS_TMP");
            stmt.execute("CREATE TABLE NSFW_DOMAINS_TMP LIKE NSFW_DOMAINS");

            List<String> combinedDangerList = new ArrayList<>(10_000);
            for (var dangerListUrl : dangerLists) {
                combinedDangerList.addAll(fetchList(client, dangerListUrl));
            }

            for (String domain : combinedDangerList) {
                insertStmt.setInt(1, NSFW_BLOCK_DANGER);
                insertStmt.setString(2, domain);
                insertStmt.execute();
            }

            List<String> combinedSmutList = new ArrayList<>(10_000);
            for (var smutListUrl : smutLists) {
                combinedSmutList.addAll(fetchList(client, smutListUrl));
            }

            for (String domain : combinedSmutList) {
                insertStmt.setInt(1, NSFW_BLOCK_SMUT);
                insertStmt.setString(2, domain);
                insertStmt.addBatch();
                insertStmt.execute();
            }

            stmt.execute("""
                    DROP TABLE IF EXISTS NSFW_DOMAINS
                    """);
            stmt.execute("""
                    RENAME TABLE NSFW_DOMAINS_TMP TO NSFW_DOMAINS
                    """);
            sync();
        }
        catch (SQLException ex) {
            logger.error("Failed to fetch NSFW domain lists", ex);
        }
     }

     public List<String> fetchList(HttpClient client, String url) {

        logger.info("Fetching NSFW domain list from {}", url);

        var request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .build();

        try {
            if (url.endsWith(".gz")) {
                var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                byte[] body = response.body();

                try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(body))))) {
                    return reader.lines()
                            .filter(StringUtils::isNotEmpty)
                            .toList();
                } catch (Exception e) {
                    logger.error("Error reading GZIP response from {}", url, e);
                }
            } else {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {

                    return Arrays.stream(StringUtils.split(response.body(), "\n"))
                            .filter(StringUtils::isNotEmpty)
                            .toList();
                } else {
                    logger.warn("Failed to fetch list from {}: HTTP {}", url, response.statusCode());
                }
            }
        }
        catch (Exception e) {
            logger.error("Error fetching NSFW domain list from {}", url, e);
        }


        return List.of();
     }
}

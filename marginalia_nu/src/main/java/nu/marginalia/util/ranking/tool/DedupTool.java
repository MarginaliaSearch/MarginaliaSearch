package nu.marginalia.util.ranking.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import org.mariadb.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DedupTool {

    private static final Logger logger = LoggerFactory.getLogger(DedupTool.class);

    public Set<String> originDomains = new HashSet<>();
    public Set<Integer> originDomainIds = new HashSet<>();
    public long domainIdMax = -1;
    public int domainCount;
    private volatile static int rankMax;

    public int maxId() {
        return (int) domainIdMax;
    }
    public int domainCount() {
        return domainCount;
    }

    static LinkedBlockingQueue<Integer> uploadQueue = new LinkedBlockingQueue<>(10);
    volatile static boolean running = true;

    @AllArgsConstructor @ToString @Getter
    static class Data {
        String url;
        int id;
        String domain;
    }

    @SneakyThrows
    public static void main(String... args) throws IOException {
        Driver driver = new Driver();
        var ds = new DatabaseModule().provideConnection();

        Map<Integer, Map<Integer, List<Data>>> domainToHashToUrl = new HashMap<>();

        try (var conn = ds.getConnection();
             var fetchStmt = conn.prepareStatement("SELECT URL_TOP_DOMAIN_ID,DATA_HASH,URL,EC_URL.ID,EC_DOMAIN.URL_PART FROM EC_URL INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_ID WHERE DATA_HASH IS NOT NULL");
             var updateStmt = conn.prepareStatement("UPDATE EC_URL SET STATE='redirect' WHERE ID=?");

             ) {
            fetchStmt.setFetchSize(10_000);
            var rsp = fetchStmt.executeQuery();
            while (rsp.next()) {
                domainToHashToUrl.computeIfAbsent(rsp.getInt(1), i -> new HashMap<>())
                        .computeIfAbsent(rsp.getInt(2), i -> new ArrayList<>()).add(new Data(rsp.getString(3), rsp.getInt(4), rsp.getString(5)));
            }


            List<Integer> updateIds = new ArrayList<>();

            domainToHashToUrl.forEach((domain, hashes) -> {
                hashes.forEach((hash, urls) -> {
                    if (urls.size() > 1) {
                        Comparator<Data> c = Comparator.comparing(d -> d.domain.length());
                        var urls2 = urls.stream().sorted(c.thenComparing(d -> d.url.length()))
                                .collect(Collectors.partitioningBy(d -> d.url.endsWith("/")));

                        Stream
                                .concat(urls2.get(true).stream(),urls2.get(false).stream()).skip(1)
                                .map(Data::getId)
                                .forEach(updateIds::add);
                    }
                });
            });

            for (int id : updateIds) {
                updateStmt.setInt(1, id);
                updateStmt.executeUpdate();
            }
        }
    }

}

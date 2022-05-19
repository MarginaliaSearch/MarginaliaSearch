package nu.marginalia.wmsa.edge.crawler.domain;

import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import it.unimi.dsi.fastutil.ints.IntArrays;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpFetcher;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklistImpl;
import nu.marginalia.util.ranking.BuggyStandardPageRank;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.mariadb.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

@Singleton
public class RssCrawler {


    static final LinkedBlockingQueue<EdgeUrl> feedsQueue = new LinkedBlockingQueue<>();
    static final LinkedBlockingQueue<UploadJob> uploadQueue = new LinkedBlockingQueue<>(2);

    @AllArgsConstructor
    static class UploadJob {
        int domainId;
        EdgeUrl[] urls;
    }
    private final HikariDataSource dataSource;

    private final HttpFetcher fetcher;
    private final LinkParser lp = new LinkParser();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static void main(String[] args) throws IOException {
        org.mariadb.jdbc.Driver driver = new Driver();

        var dbModule = new DatabaseModule();
        new RssCrawler(dbModule.provideConnection()).run();
    }

    @Inject
    public RssCrawler(HikariDataSource dataSource) {

        this.dataSource = dataSource;
        this.fetcher = new HttpFetcher("search.marginalia.nu");
        fetcher.setAllowAllContentTypes(true);
    }

    @SneakyThrows
    public void run() {
        var rank = new BuggyStandardPageRank(dataSource, "memex.marginalia.nu");
        var nodes = rank.pageRankWithPeripheralNodes(rank.size(), false);

        EdgeDomainBlacklistImpl blacklist = new EdgeDomainBlacklistImpl(dataSource);

        TIntIntHashMap domainRankById = new TIntIntHashMap(nodes.size(), 0.5f, 0, Integer.MAX_VALUE);

        for (int i = 0; i < nodes.size(); i++) {
            if (!blacklist.isBlacklisted(nodes.get(i))) {
                domainRankById.put(nodes.get(i), i);
            }
        }

        List<EdgeUrl> feedUrls = new ArrayList<>(15_000);

        TIntArrayList feedDomainIds = new TIntArrayList();
        try (var conn = dataSource.getConnection()) {

            try (var stmt = conn.prepareStatement("SELECT DISTINCT(DOMAIN_ID) FROM EC_FEED_URL INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_ID ORDER BY RANK ASC")) {
                stmt.setFetchSize(1000);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    int id = rsp.getInt(1);

                    if (domainRankById.get(id) < rank.size()) {
                        feedDomainIds.add(id);
                    }
                }
            }

            int[] ids = feedDomainIds.toArray();
            IntArrays.quickSort(ids, (a,b) -> domainRankById.get(a) - domainRankById.get(b));

            for (int i = 0; i < ids.length; i++) {
                try (var stmt = conn.prepareStatement("SELECT DOMAIN_ID, PROTO, URL_PART, PORT, URL from EC_FEED_URL INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_ID WHERE DOMAIN_ID=? ORDER BY LENGTH(URL) ASC LIMIT 1")) {
                    stmt.setFetchSize(10);
                    stmt.setInt(1, ids[i]);
                    var rsp = stmt.executeQuery();
                    while (rsp.next()) {
                        var url = new EdgeUrl(rsp.getString(2), new EdgeDomain(rsp.getString(3)), rsp.getInt(4), rsp.getString(5));
                        feedUrls.add(url);
                    }
                }
            }
        }
        catch (Exception ex) {
            logger.error("SQL error", ex);
        }



        feedsQueue.addAll(feedUrls);

        List<Thread> threads = new ArrayList<>();
        threads.add(new Thread(this::uploadThread, "Uploader"));
        for (int i = 0; i < 256; i++) {
            threads.add(new Thread(this::processor, "Processor"));
        }

        threads.forEach(Thread::start);

        Thread.sleep(5*60_000);
        threads.forEach(Thread::interrupt);
        Thread.sleep(60_000);

        System.exit(0);
    }

    @SneakyThrows
    private void processor() {
        EdgeDataStoreDaoImpl dataStoreDao = new EdgeDataStoreDaoImpl(dataSource);

        while (!feedsQueue.isEmpty()) {
            try {

                var url = feedsQueue.take();
                logger.info("{}", url);

                var domainId = dataStoreDao.getDomainId(url.getDomain());
                var contents = fetcher.fetchContent(url);

                if (null != contents) {
                    List<EdgeUrl> urls = getLinks(url, contents.data);
                    urls = dataStoreDao.getNewUrls(domainId, urls);
                    if (!urls.isEmpty()) {
                        uploadQueue.put(new UploadJob(domainId.getId(), urls.toArray(EdgeUrl[]::new)));
                    }
                }
            }
            catch (InterruptedException ex) {
                break;
            }
            catch (Exception ex) {
                //
            }
        }
        logger.info("Processor done");
    }

    private List<EdgeUrl> getLinks(EdgeUrl base, String str) {

    var doc = Jsoup.parse(str.replaceAll("link", "lnk"));

    Set<EdgeUrl> urls = new LinkedHashSet<>();

    doc.select("entry > lnk[rel=alternate]").forEach(element -> {
        var href = element.attr("href");
        if (href != null && !href.isBlank()) {
            lp.parseLink(base, href)
                    .filter(u -> Objects.equals(u.domain.domain, base.domain.domain))
                    .filter(u -> u.proto.startsWith("http"))
                    .ifPresent(urls::add);
        }
    });

    doc.getElementsByTag("lnk").forEach(element -> {
        var href = element.text();
        if (href != null && !href.isBlank()) {
            lp.parseLink(base, href)
                    .filter(u -> Objects.equals(u.domain.domain, base.domain.domain))
                    .filter(u -> u.proto.startsWith("http"))
                    .ifPresent(urls::add);
        }
    });

    doc.select("item > guid[isPermalink=true]").forEach(element -> {
        var href = element.text();
        if (href != null && !href.isBlank()) {
            lp.parseLink(base, href)
                    .filter(u -> Objects.equals(u.domain.domain, base.domain.domain))
                    .filter(u -> u.proto.startsWith("http"))
                    .ifPresent(urls::add);
        }
    });

    return new ArrayList<>(urls);
}

    @SneakyThrows
    public void uploadThread() {
        EdgeDataStoreDaoImpl dao = new EdgeDataStoreDaoImpl(dataSource);
        try (var conn = dataSource.getConnection(); var stmt = conn.prepareStatement("UPDATE EC_DOMAIN SET STATE=0, INDEXED=LEAST(8, INDEXED) WHERE ID=?")) {
            while (!feedsQueue.isEmpty() || !uploadQueue.isEmpty()) {
                var job = uploadQueue.take();
                dao.putUrl(-5., job.urls);

                stmt.setInt(1, job.domainId);
                stmt.executeUpdate();

                logger.info("{}[{}]", job.urls[0].domain, job.urls.length);
            }
        }
        logger.info("Uploader done");
    }
}

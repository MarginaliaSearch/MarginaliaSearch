package nu.marginalia.experimental;

import lombok.SneakyThrows;
import nu.marginalia.converting.processor.DocumentProcessor;
import nu.marginalia.converting.processor.logic.topic.AdblockSimulator;
import nu.marginalia.crawling.common.plan.CrawlPlanLoader;
import nu.marginalia.crawling.common.plan.EdgeCrawlPlan;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.service.module.DatabaseModule;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;


public class CrawlDataExtractorTool {
    private static final AdblockSimulator abs;

    static {
        try {
            abs = new AdblockSimulator();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Set<String> urls = new HashSet<>(50_000_000);

    @SneakyThrows
    public static void main(String... args) throws IOException {
        EdgeCrawlPlan plan = new CrawlPlanLoader().load(Path.of(args[0]));
        DatabaseModule module = new DatabaseModule();

        try (var ds = module.provideConnection();
             var conn = ds.getConnection();
             var stmt = conn.createStatement()) {
            var rsp = stmt.executeQuery("SELECT URL FROM EC_URL_VIEW WHERE TITLE IS NOT NULL");
            while (rsp.next()) {
                urls.add(rsp.getString(1));
            }
        }
        catch (SQLException ex) {
            ex.printStackTrace();
        }

        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(10);
        ExecutorService pool = new ThreadPoolExecutor(10, 20, 5, TimeUnit.MINUTES, queue);
        Semaphore sem = new Semaphore(20);

        try (var iterable = plan.domainsIterable()) {
            for (var domain : iterable) {
                sem.acquire();
                pool.execute(() -> {
                    try { processDomain(domain); }
                    finally { sem.release(); }
                });
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        pool.shutdown();

        while (!pool.awaitTermination(1, TimeUnit.MINUTES));
    }

    private static void processDomain(CrawledDomain domain) {
        if (domain.doc == null) return;
        for (var doc : domain.doc) {
            if (!urls.contains(doc.url))
                continue;

            if (DocumentProcessor.isAcceptedContentType(doc) && "OK".equals(doc.crawlerStatus)) {
                processDocument(doc);
            }
        }
    }


    private static void processDocument(CrawledDocument doc) {
        Document parsedDocument = Jsoup.parse(doc.documentBody.decode());

        if (abs.hasAds(parsedDocument)) {
            System.out.println(doc.url);
        }
    }
}

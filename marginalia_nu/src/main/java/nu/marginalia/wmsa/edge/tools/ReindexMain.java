package nu.marginalia.wmsa.edge.tools;

import com.opencsv.exceptions.CsvValidationException;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.array.TIntArrayList;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.archive.client.ArchiveClient;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.*;
import nu.marginalia.wmsa.edge.crawler.domain.language.LanguageFilter;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlProcessor;
import nu.marginalia.wmsa.edge.crawler.domain.processor.PlainTextProcessor;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpFetcher;
import nu.marginalia.wmsa.edge.crawler.worker.GeoIpBlocklist;
import nu.marginalia.wmsa.edge.crawler.worker.IpBlockList;
import nu.marginalia.wmsa.edge.crawler.worker.UploaderWorker;
import nu.marginalia.wmsa.edge.crawler.worker.facade.UploadFacadeDirectImpl;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklistImpl;
import nu.marginalia.wmsa.edge.director.client.EdgeDirectorClient;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainLink;
import nu.marginalia.wmsa.edge.model.crawl.EdgeIndexTask;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlVisit;
import org.mariadb.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ReindexMain {
    static LinkedBlockingQueue<ReindexJob> processQueue = new LinkedBlockingQueue<>(5);
    static LinkedBlockingQueue<UploadJob> uploadQueue = new LinkedBlockingQueue<>(2);

    static Logger logger = LoggerFactory.getLogger(ReindexMain.class);

    static HikariDataSource conn;

    private static IpBlockList blocklist;
    private static HttpFetcher fetcher;
    private static UploadFacadeDirectImpl uploadFacade;

    static {
        try {
            blocklist = new IpBlockList(new GeoIpBlocklist());
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
        }
    }

    @AllArgsConstructor
    static class ReindexJob {
        EdgeIndexTask task;
        Map<EdgeUrl, Integer> hashes;
        int visitedCount;
    };

    @AllArgsConstructor
    static class UploadJob {
        DomainCrawlResults results;
        ReindexJob job;
    };


    static volatile boolean running = true;

    public static class AbortMonitor {
        private volatile boolean abort = false;
        private static volatile AbortMonitor instance = null;

        public static AbortMonitor getInstance() {
            if (instance == null) {
                synchronized (AbortMonitor.class) {
                    if (instance == null) {
                        instance = new AbortMonitor();
                        new Thread(instance::run, "AbortMon").start();
                    }
                }
            }
            return instance;
        }

        private AbortMonitor() {
        }

        @SneakyThrows
        public void run() {
            for (;;) {
                Thread.sleep(1000);
                if (Files.exists(Path.of("/tmp/stop"))) {
                    logger.warn("Abort file found");
                    abort = true;
                    Files.delete(Path.of("/tmp/stop"));
                }
            }
        }
        public boolean isAlive() {
            return !abort;
        }
    }

    @SneakyThrows
    public static void main(String... args) throws IOException {
        Driver driver = new Driver();

        conn = new DatabaseModule().provideConnection();
        var blacklist = new EdgeDomainBlacklistImpl(conn);

        EdgeDataStoreDaoImpl dataStoreDao = new EdgeDataStoreDaoImpl(conn);

        TIntArrayList domainIndexOrder = new TIntArrayList();

        new Thread(ReindexMain::uploadThread, "Uploader").start();

        SentenceExtractor newSe = new SentenceExtractor(lm);
        DocumentKeywordExtractor documentKeywordExtractor = new DocumentKeywordExtractor(dict);

        uploadFacade = new UploadFacadeDirectImpl(new EdgeDataStoreDaoImpl(conn), new EdgeIndexClient(), new EdgeDirectorClient());

        fetcher = new HttpFetcher("search.marginalia.nu");
        var dcf = new DomainCrawlerFactory(fetcher,
                new HtmlProcessor(documentKeywordExtractor, newSe),
                new PlainTextProcessor(documentKeywordExtractor, newSe),
                new ArchiveClient(),
                new DomainCrawlerRobotsTxt(fetcher, "search.marginalia.nu"),
                new LanguageFilter(),
                blocklist);

        for (int i = 0; i < 512; i++) {
            new Thread(() -> processorThread(dcf), "Processor-"+i).start();
        }

        try (var c = conn.getConnection();
             var fetchDomains = c.prepareStatement("select ID, EC_DOMAIN.URL_PART from EC_DOMAIN WHERE QUALITY_RAW>-100 AND INDEXED>0 AND INDEX_DATE<'2022-03-17' AND STATE<2 ORDER BY INDEX_DATE ASC,DISCOVER_DATE ASC,STATE DESC,INDEXED DESC,EC_DOMAIN.ID");
             var fetchUrlsForDomain = c.prepareStatement("select ID,DATA_HASH,VISITED FROM EC_URL WHERE DOMAIN_ID=? ORDER BY VISITED DESC, DATA_HASH IS NOT NULL DESC, ID")
        ) {
            fetchDomains.setFetchSize(10_000);
            fetchDomains.executeQuery();
            var domainRsp = fetchDomains.executeQuery();

            logger.info("Fetched domains");
            while (domainRsp.next()) {
                if (!blacklist.isBlacklisted(domainRsp.getInt(1))) {
                    domainIndexOrder.add(domainRsp.getInt(1));
                }
            }

            fetchUrlsForDomain.setFetchSize(10_000);

            for (int i = 0; i < domainIndexOrder.size(); i++) {
                if (!AbortMonitor.getInstance().isAlive()) {
                    break;
                }

                int domainId = domainIndexOrder.getQuick(i);
                var domain = dataStoreDao.getDomain(new EdgeId<>(domainId));

                fetchUrlsForDomain.setInt(1, domainId);
                var urlRsp = fetchUrlsForDomain.executeQuery();

                EdgeIndexTask task = new EdgeIndexTask(domain, 1000, 1000, 0);
                Map<EdgeUrl, Integer> hashes = new HashMap<>();

                int visitedCount = 0;
                while (urlRsp.next()) {
                    var url = dataStoreDao.getUrl(new EdgeId<>(urlRsp.getInt(1)));
                    task.urls.add(url);

                    if (urlRsp.getBoolean(3)) {
                        visitedCount++;
                    }

                    int hash = urlRsp.getInt(2);
                    if (hash != 0)
                        hashes.put(url, hash);
                }

                processQueue.put(new ReindexJob(task, hashes, visitedCount));
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }


        logger.warn("Terminating Main");
    }

    static LanguageModels lm = new LanguageModels(
            Path.of("/var/lib/wmsa/model/ngrams-generous-emstr.bin"),
            Path.of("/var/lib/wmsa/model/tfreq-new-algo3.bin"),
            Path.of("/var/lib/wmsa/model/opennlp-sentence.bin"),
            Path.of("/var/lib/wmsa/model/English.RDR"),
            Path.of("/var/lib/wmsa/model/English.DICT"),
            Path.of("/var/lib/wmsa/model/opennlp-tok.bin")
    );
    static NGramDict dict = new NGramDict(lm);

    private static final Semaphore processSem = new Semaphore(500, true);
    @SneakyThrows
    public static void processorThread(DomainCrawlerFactory dcf) {
        String name = Thread.currentThread().getName();
        try {
outer:
            while (AbortMonitor.getInstance().isAlive() && (running || !processQueue.isEmpty())) {
                Thread.currentThread().setName(name);
                ReindexJob job = null;

                while (job == null) {
                    job = processQueue.poll(30, TimeUnit.SECONDS);
                    if (!AbortMonitor.getInstance().isAlive()) {
                        break outer;
                    }
                }

                Thread.currentThread().setName(name + ":" + job.task.domain);
                if (!blocklist.isAllowed(job.task.domain)) {
                    setDomainError(job.task.domain, new HttpFetcher.FetchResult(HttpFetcher.FetchResultState.ERROR, job.task.domain));
                }

                var probe = fetcher.probeDomain(job.task.urls.get(0));

                if (!AbortMonitor.getInstance().isAlive()) {
                    break;
                }

                if (!probe.ok()) {
                    setDomainError(job.task.domain, probe);
                } else {
                    var dc = dcf.domainCrawler(job.task);

                    int countMaxAll = 4000;
                    int countVisited = (int) (job.visitedCount * 1.1);
                    int countHash = 100 + job.hashes.size() * 2;

                    int maxCount = Math.min(countMaxAll, Math.max(countVisited, countHash));

                    DomainCrawlResults result;
                    int tokens = Math.max(1,maxCount/1000);
                    try {
                        while (!processSem.tryAcquire(tokens))
                            Thread.sleep((int)(100 + Math.random() * 100));
                        result = dc.crawlToExhaustion(maxCount, AbortMonitor.getInstance()::isAlive);

                        if (!AbortMonitor.getInstance().isAlive()) {
                            break;
                        }
                    }
                    finally {
                        processSem.release(tokens);
                    }

                    uploadQueue.put(new UploadJob(result, job));
                }
            }
        }
        catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        logger.warn("Terminating {}", Thread.currentThread().getName());
    }

    private static void setDomainError(EdgeDomain domain, HttpFetcher.FetchResult probe) {
        List<EdgeDomainLink> links = new ArrayList<>(1);
        EdgeDomain alias = null;
        if (probe.state == HttpFetcher.FetchResultState.REDIRECT) {
            links.add(new EdgeDomainLink(domain, probe.domain));
            alias = probe.domain;
        }
        uploadFacade.putLinks(links, true);
        uploadFacade.updateDomainIndexTimestamp(domain, EdgeDomainIndexingState.ERROR, alias, 1);
    }

    @SneakyThrows
    public static void uploadThread() {
        int count = 0;
        long allUrls = 0;
        long newUrls = 0;
outer:
        while (AbortMonitor.getInstance().isAlive() && (running || !processQueue.isEmpty() || !uploadQueue.isEmpty())) {

            UploadJob job = null;

            while (job == null) {
                job = uploadQueue.poll(30, TimeUnit.SECONDS);
                if (!AbortMonitor.getInstance().isAlive()) {
                    break outer;
                }
            }

            UploadJob data = job;


            var dc = data.results;

            double avgQuality = UploaderWorker.calculateMedianQuality(dc).orElse(-5.);

            if (uploadFacade.isBlacklisted(dc.domain)) {
                continue;
            }

            final double linkQualityRating = -5; //(avgQuality + UNKNOWN_SITE_ATTRACTOR)/2 - extLinkPenalty;

            var visits = dc.visits();
            allUrls += visits.size();

            var newContents = visits;

            /*.stream().filter(visit -> {
                var hash = data.job.hashes.get(visit.url);
                return (hash == null || !Objects.equals(visit.data_hash_code, hash));
            }).collect(Collectors.toList());*/

            var goodUrls = newContents.stream().map(EdgeUrlVisit::getUrl).collect(Collectors.toSet());

            newUrls += newContents.size();

            uploadFacade.putUrls(dc.extUrl, linkQualityRating);
            uploadFacade.putUrls(dc.intUrl, linkQualityRating);
            uploadFacade.putUrlVisits(newContents);
            uploadFacade.putFeeds(dc.feeds);

            if (avgQuality < UploaderWorker.QUALITY_LOWER_BOUND_CUTOFF) {
                uploadFacade.updateDomainIndexTimestamp(dc.domain, EdgeDomainIndexingState.ACTIVE, null, 1);
                continue;
            }

            uploadFacade.putLinks(dc.links, true);
            uploadFacade.putWords(dc.pageContents.values().stream().filter(pc -> goodUrls.contains(pc.url)).collect(Collectors.toList()), 1);

            uploadFacade.updateDomainIndexTimestamp(dc.domain, EdgeDomainIndexingState.ACTIVE, null, Math.max(1, visits.size()/50));

            logger.info("{} Done - {} : {} : {}", ++count, data.results.domain, allUrls, newUrls);
        }

        logger.warn("Terminating {}", Thread.currentThread().getName());
    }

}

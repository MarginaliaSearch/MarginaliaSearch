package nu.marginalia.wmsa.edge.crawler.worker;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.crawler.domain.DomainCrawlResults;
import nu.marginalia.wmsa.edge.crawler.worker.facade.UploadFacade;
import nu.marginalia.wmsa.edge.crawler.worker.results.WorkerResults;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class UploaderWorker implements Runnable {
    private final List<LinkedBlockingQueue<WorkerResults>> queues;
    private final UploadFacade uploadFacade;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final static double UNKNOWN_SITE_ATTRACTOR = -2.5;
    public final static double QUALITY_LOWER_BOUND_CUTOFF = -15;

    private static final Counter wmsa_edge_crawler_pages_indexed = Counter.build("wmsa_edge_crawler_pages_indexed", "Pages Indexed")
                   .register();
    private static final Counter wmsa_edge_crawler_domains_indexed = Counter.build("wmsa_edge_crawler_domains_indexed", "Domains Indexed")
                   .register();
    private static final Counter wmsa_edge_crawler_links_discovered = Counter.build("wmsa_edge_crawler_links_discovered", "Links Discovered")
                   .register();
    private static final Counter wmsa_edge_crawler_duds = Counter.build("wmsa_edge_crawler_duds", "Duds")
                   .register();
    private static final Counter wmsa_edge_crawler_domain_alias =  Counter.build("wmsa_edge_crawler_domain_alias", "Alias")
                   .register();
    private static final Histogram wmsa_edge_crawler_publish_time = Histogram.build("wmsa_edge_crawler_publish_time", "Post wait")
                   .register();
    private static final Histogram wmsa_uploader_job_wait_time = Histogram.build("wmsa_uploader_job_wait_time", "Underrun Time")
                   .register();

    public UploaderWorker(List<LinkedBlockingQueue<WorkerResults>> queues, UploadFacade uploadFacade) {
        this.queues = queues;
        this.uploadFacade = uploadFacade;
    }

    volatile int queueDepth = 0;

    @SneakyThrows
    @Override
    public void run() {
        uploaderThread();
    }

    @SneakyThrows
    private void uploaderThread() {
        for (;;) {
            long waitStart = System.currentTimeMillis();
            int waitTicks = 0;

            updateQueueDepth();

            for (var queue : queues) {
                WorkerResults res;

                if (waitTicks++ < queues.size()) {
                    res = queue.poll();
                }
                else {
                    res = queue.poll(10, TimeUnit.MILLISECONDS);
                }

                try {
                    if (null != res) {
                        waitTicks = 0;
                        wmsa_uploader_job_wait_time.observe(System.currentTimeMillis() - waitStart);
                        res.upload(this);
                        waitStart = System.currentTimeMillis();
                    }
                } catch (Exception ex) {
                    logger.error("Error", ex);
                }
            }
        }
    }

    private void updateQueueDepth() {
        int qd = 0;
        for (var queue : queues) {
            qd += queue.size();
        }
        queueDepth = qd;
    }


    @SneakyThrows
    public void onDomainCrawlResults(DomainCrawlResults dc) {
        while (uploadFacade.isBlocked()) {
            Thread.sleep(1000);
        }
        var domain = dc.domain;

        long start = System.currentTimeMillis();

        updateStatsForResults(dc);

        double avgQuality = calculateMedianQuality(dc).orElse(-5.);

        if (logger.isInfoEnabled()) {

            String log = String.format("QD:%2d\t%2d\tQ:%4.2f\tR:%4.2f\t%3d\t%4.2f\t%s",
                    queueDepth, dc.pass,
                    Math.round(100 * avgQuality) / 100.,
                    Math.round(10000 * (1 - dc.rank)) / 100.,
                    dc.pageContents.size(),
                    (System.currentTimeMillis() - dc.crawlStart) / 1000.,
                    domain);

            logger.info(log);
        }

        uploadResults(dc, avgQuality);

        double depthPenalty = dc.pass / 250.;
        uploadFacade.finishTask(domain, avgQuality - depthPenalty, EdgeDomainIndexingState.ACTIVE);

        wmsa_edge_crawler_publish_time.observe(System.currentTimeMillis() - start);
    }

    private void updateStatsForResults(DomainCrawlResults dc) {
        if (dc.pageContents.isEmpty()) {
            wmsa_edge_crawler_duds.inc();
        }

        wmsa_edge_crawler_domains_indexed.inc();
        wmsa_edge_crawler_pages_indexed.inc(dc.pageContents.size());
        wmsa_edge_crawler_links_discovered.inc(dc.extUrl.size());
    }

    public static OptionalDouble calculateMedianQuality(DomainCrawlResults dc) {

        double[] qualities =  dc.pageContents.values().stream().mapToDouble(page -> page.metadata.quality()).sorted().toArray();
        if (qualities.length <= 5) {
            return Arrays.stream(qualities).average();
        }
        else {
            return OptionalDouble.of(qualities[qualities.length/2]);
        }
    }

    public static double calculateExternalLinkPenalty(DomainCrawlResults dc) {
        return dc.extUrl.size() / ((1+dc.pageContents.size())*50.);
    }

    private void uploadResults(DomainCrawlResults dc, double avgQuality) {

        final double extLinkPenalty = calculateExternalLinkPenalty(dc);
        if (uploadFacade.isBlacklisted(dc.domain)) {
            return;
        }

        final double linkQualityRating = -5; //(avgQuality + UNKNOWN_SITE_ATTRACTOR)/2 - extLinkPenalty;

        uploadFacade.putUrls(dc.extUrl, linkQualityRating);
        uploadFacade.putUrls(dc.intUrl, linkQualityRating);
        uploadFacade.putUrlVisits(dc.visits());
        uploadFacade.putFeeds(dc.feeds);

        if (avgQuality < QUALITY_LOWER_BOUND_CUTOFF) {
            return;
        }

        uploadFacade.putLinks(dc.links, false);
        uploadFacade.putWords(dc.pageContents.values(), 0);
    }

    public void onDomainAlias(EdgeDomain source, EdgeDomain dest, EdgeUrl[] urls) {
        wmsa_edge_crawler_domain_alias.inc();

        if (urls.length == 0) {
            wmsa_edge_crawler_duds.inc();
        }

        long start = System.currentTimeMillis();
        uploadFacade.putDomainAlias(source, dest);
        uploadFacade.putUrls(Arrays.asList(urls), -2);
        uploadFacade.finishTask(source, -1000, EdgeDomainIndexingState.REDIR);

        wmsa_edge_crawler_publish_time.observe(System.currentTimeMillis() - start);
    }

    public void onInvalidDomain(EdgeDomain domain, String why) {
        logger.warn("Setting domain {} state to ERROR: {}", domain, why);
        long start = System.currentTimeMillis();
        uploadFacade.finishTask(domain, -1000, EdgeDomainIndexingState.ERROR);
        wmsa_edge_crawler_publish_time.observe(System.currentTimeMillis() - start);
    }
}

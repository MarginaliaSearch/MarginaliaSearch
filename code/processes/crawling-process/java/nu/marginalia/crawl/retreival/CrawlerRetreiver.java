package nu.marginalia.crawl.retreival;

import crawlercommons.robots.SimpleRobotRules;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.contenttype.ContentType;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.retreival.revisit.CrawlerRevisitor;
import nu.marginalia.crawl.retreival.revisit.DocumentWithReference;
import nu.marginalia.crawl.retreival.sitemap.SitemapFetcher;
import nu.marginalia.crawling.body.HttpFetchResult;
import nu.marginalia.crawling.model.*;
import nu.marginalia.ip_blocklist.UrlBlocklist;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class CrawlerRetreiver implements AutoCloseable {

    private static final int MAX_ERRORS = 20;
    private static final int HTTP_429_RETRY_LIMIT = 1; // Retry 429s once

    private final HttpFetcher fetcher;

    private final String domain;

    private static final LinkParser linkParser = new LinkParser();
    private static final Logger logger = LoggerFactory.getLogger(CrawlerRetreiver.class);

    private static final UrlBlocklist urlBlocklist = new UrlBlocklist();
    private static final LinkFilterSelector linkFilterSelector = new LinkFilterSelector();

    private final DomainProber domainProber;
    private final DomainCrawlFrontier crawlFrontier;
    private final WarcRecorder warcRecorder;
    private final CrawlerRevisitor crawlerRevisitor;

    private final SitemapFetcher sitemapFetcher;
    int errorCount = 0;

    public CrawlerRetreiver(HttpFetcher fetcher,
                            DomainProber domainProber,
                            CrawlSpecRecord specs,
                            WarcRecorder warcRecorder)
    {
        this.warcRecorder = warcRecorder;
        this.fetcher = fetcher;
        this.domainProber = domainProber;

        domain = specs.domain;

        crawlFrontier = new DomainCrawlFrontier(new EdgeDomain(domain), Objects.requireNonNullElse(specs.urls, List.of()), specs.crawlDepth);
        crawlerRevisitor = new CrawlerRevisitor(crawlFrontier, this, warcRecorder);
        sitemapFetcher = new SitemapFetcher(crawlFrontier, fetcher.createSitemapRetriever());

        // We must always crawl the index page first, this is assumed when fingerprinting the server
        var fst = crawlFrontier.peek();
        if (fst != null) {
            // Ensure the index page is always crawled
            var root = fst.withPathAndParam("/", null);

            crawlFrontier.addFirst(root);
        }
    }

    // For testing
    public DomainCrawlFrontier getCrawlFrontier() {
        return crawlFrontier;
    }

    public int fetch() {
        return fetch(new DomainLinks(), new CrawlDataReference());
    }

    public int fetch(DomainLinks domainLinks, CrawlDataReference oldCrawlData) {
        final DomainProber.ProbeResult probeResult = domainProber.probeDomain(
                fetcher,
                domain,
                new EdgeUrl("http", new EdgeDomain(domain), null, "/", null));

        try {
            // Sleep a bit to avoid hammering the server with requests, we just probed it
            TimeUnit.SECONDS.sleep(1);

            // Fetch the domain
            return crawlDomain(oldCrawlData, probeResult, domainLinks);
        }
        catch (Exception ex) {
            logger.error("Error crawling domain {}", domain, ex);
            return 0;
        }
    }

    public void syncAbortedRun(Path warcFile) {
        var resync = new CrawlerWarcResynchronizer(crawlFrontier, warcRecorder);

        resync.run(warcFile);
    }

    private int crawlDomain(CrawlDataReference oldCrawlData, DomainProber.ProbeResult probeResult, DomainLinks domainLinks) throws IOException, InterruptedException {
        String ip = findIp(domain);
        EdgeUrl rootUrl;

        warcRecorder.writeWarcinfoHeader(ip, new EdgeDomain(domain), probeResult);

        if (!(probeResult instanceof DomainProber.ProbeResultOk ok)) {
            return 1;
        }
        else {
            rootUrl = ok.probedUrl();
        }

        final SimpleRobotRules robotsRules = fetcher.fetchRobotRules(rootUrl.domain, warcRecorder);
        final CrawlDelayTimer delayTimer = new CrawlDelayTimer(robotsRules.getCrawlDelay());

        delayTimer.waitFetchDelay(0); // initial delay after robots.txt
        sniffRootDocument(rootUrl, delayTimer);
        delayTimer.waitFetchDelay(0); // delay after sniffing

        // Play back the old crawl data (if present) and fetch the documents comparing etags and last-modified
        int recrawled = crawlerRevisitor.recrawl(oldCrawlData, robotsRules, delayTimer);

        if (recrawled > 0) {
            // If we have reference data, we will always grow the crawl depth a bit
            crawlFrontier.increaseDepth(1.5, 2500);
        }

        // Add external links to the crawl frontier
        crawlFrontier.addAllToQueue(domainLinks.getUrls(rootUrl.proto));

        // Add links from the sitemap to the crawl frontier
        sitemapFetcher.downloadSitemaps(robotsRules, rootUrl);

        CrawledDomain ret = new CrawledDomain(domain,
                null,
                CrawlerDomainStatus.OK.name(),
                null,
                ip,
                new ArrayList<>(),
                null);

        int fetchedCount = recrawled;

        while (!crawlFrontier.isEmpty()
            && !crawlFrontier.isCrawlDepthReached()
            && errorCount < MAX_ERRORS
            && !Thread.interrupted())
        {
            var top = crawlFrontier.takeNextUrl();

            if (!robotsRules.isAllowed(top.toString())) {
                warcRecorder.flagAsRobotsTxtError(top);
                continue;
            }

            // Check the link filter if the endpoint should be fetched based on site-type
            if (!crawlFrontier.filterLink(top))
                continue;

            // Check vs blocklist
            if (urlBlocklist.isUrlBlocked(top))
                continue;

            if (!isAllowedProtocol(top.proto))
                continue;

            // Check if the URL is too long to insert into the DB
            if (top.toString().length() > 255)
                continue;

            if (!crawlFrontier.addVisited(top))
                continue;


            try {
                if (fetchWriteAndSleep(top, delayTimer, DocumentWithReference.empty()).isOk()) {
                    fetchedCount++;
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        ret.cookies = fetcher.getCookies();

        return fetchedCount;
    }

    private void sniffRootDocument(EdgeUrl rootUrl, CrawlDelayTimer timer) {
        try {
            logger.debug("Configuring link filter");

            var url = rootUrl.withPathAndParam("/", null);

            HttpFetchResult result = null;

            for (int i = 0; i <= HTTP_429_RETRY_LIMIT; i++) {
                try {
                    result = fetcher.fetchContent(url, warcRecorder, ContentTags.empty());
                    break;
                }
                catch (RateLimitException ex) {
                    timer.waitRetryDelay(ex);
                }
                catch (Exception ex) {
                    logger.warn("Failed to fetch {}", url, ex);
                    result = new HttpFetchResult.ResultException(ex);
                }
            }

            if (!(result instanceof HttpFetchResult.ResultOk ok))
                return;

            var optDoc = ok.parseDocument();
            if (optDoc.isEmpty())
                return;

            // Sniff the software based on the sample document
            var doc = optDoc.get();
            crawlFrontier.setLinkFilter(linkFilterSelector.selectFilter(doc));

            for (var link : doc.getElementsByTag("link")) {
                String rel = link.attr("rel");
                String type = link.attr("type");

                if (!rel.equalsIgnoreCase("alternate"))
                    continue;

                if (!(type.equalsIgnoreCase("application/atom+xml")
                   || type.equalsIgnoreCase("application/rss+xml")))
                    continue;

                String href = link.attr("href");

                linkParser.parseLink(url, href)
                        .filter(crawlFrontier::isSameDomain)
                        .map(List::of)
                        .ifPresent(sitemapFetcher::downloadSitemaps);
            }
        }
        catch (Exception ex) {
            logger.error("Error configuring link filter", ex);
        }
        finally {
            crawlFrontier.addVisited(rootUrl);
        }
    }

    public HttpFetchResult fetchWriteAndSleep(EdgeUrl top,
                                              CrawlDelayTimer timer,
                                              DocumentWithReference reference) throws InterruptedException
    {
        logger.debug("Fetching {}", top);

        HttpFetchResult fetchedDoc = new HttpFetchResult.ResultNone();

        long startTime = System.currentTimeMillis();
        var contentTags = reference.getContentTags();

        // Fetch the document, retrying if we get a rate limit exception
        for (int i = 0; i <= HTTP_429_RETRY_LIMIT; i++) {
            try {
                fetchedDoc = fetcher.fetchContent(top, warcRecorder, contentTags);
                break;
            }
            catch (RateLimitException ex) {
                timer.waitRetryDelay(ex);
            }
            catch (Exception ex) {
                logger.warn("Failed to fetch {}", top, ex);
                fetchedDoc = new HttpFetchResult.ResultException(ex);
            }
        }

        // Parse the document and enqueue links
        try {
            if (fetchedDoc instanceof HttpFetchResult.ResultOk ok) {
                var docOpt = ok.parseDocument();
                if (docOpt.isPresent()) {
                    var doc = docOpt.get();

                    crawlFrontier.enqueueLinksFromDocument(top, doc);
                    crawlFrontier.addVisited(new EdgeUrl(ok.uri()));
                }
            }
            else if (fetchedDoc instanceof HttpFetchResult.Result304Raw && reference.doc() != null) {
                var doc = reference.doc();

                warcRecorder.writeReferenceCopy(top, doc.contentType, doc.httpStatus, doc.documentBody, contentTags);

                fetchedDoc = new HttpFetchResult.Result304ReplacedWithReference(doc.url,
                        new ContentType(doc.contentType, "UTF-8"),
                        doc.documentBody);

                if (doc.documentBody != null) {
                    var parsed = Jsoup.parse(doc.documentBody);

                    crawlFrontier.enqueueLinksFromDocument(top, parsed);
                    crawlFrontier.addVisited(top);
                }
            }
            else if (fetchedDoc instanceof HttpFetchResult.ResultException) {
                errorCount ++;
            }
        }
        catch (Exception ex) {
            logger.error("Error parsing document {}", top, ex);
        }

        timer.waitFetchDelay(System.currentTimeMillis() - startTime);

        return fetchedDoc;
    }

    private boolean isAllowedProtocol(String proto) {
        return proto.equalsIgnoreCase("http")
                || proto.equalsIgnoreCase("https");
    }

    private String findIp(String domain) {
        try {
            return InetAddress.getByName(domain).getHostAddress();
        } catch (UnknownHostException e) {
            return "";
        }
    }

    @Override
    public void close() throws Exception {
        warcRecorder.close();
    }

}

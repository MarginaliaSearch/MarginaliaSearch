package nu.marginalia.crawl.retreival;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import crawlercommons.robots.SimpleRobotRules;
import lombok.SneakyThrows;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawl.retreival.fetcher.SitemapRetriever;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.retreival.revisit.CrawlerRevisitor;
import nu.marginalia.crawl.retreival.revisit.DocumentWithReference;
import nu.marginalia.crawl.retreival.sitemap.SitemapFetcher;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.crawling.model.*;
import nu.marginalia.ip_blocklist.UrlBlocklist;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class CrawlerRetreiver implements AutoCloseable {

    private static final int MAX_ERRORS = 20;

    private final HttpFetcher fetcher;

    private final String domain;
    private final Consumer<SerializableCrawlData> crawledDomainWriter;

    private static final LinkParser linkParser = new LinkParser();
    private static final Logger logger = LoggerFactory.getLogger(CrawlerRetreiver.class);

    private static final HashFunction hashMethod = Hashing.murmur3_128(0);
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
                            WarcRecorder warcRecorder,
                            Consumer<SerializableCrawlData> writer)
    {
        this.warcRecorder = warcRecorder;
        this.fetcher = fetcher;
        this.domainProber = domainProber;

        domain = specs.domain;

        crawledDomainWriter = writer;


        crawlFrontier = new DomainCrawlFrontier(new EdgeDomain(domain), Objects.requireNonNullElse(specs.urls, List.of()), specs.crawlDepth);
        crawlerRevisitor = new CrawlerRevisitor(crawlFrontier, crawledDomainWriter, this, warcRecorder);
        sitemapFetcher = new SitemapFetcher(crawlFrontier, fetcher.createSitemapRetriever());

        // We must always crawl the index page first, this is assumed when fingerprinting the server
        var fst = crawlFrontier.peek();
        if (fst != null) {
            // Ensure the index page is always crawled
            var root = fst.withPathAndParam("/", null);

            crawlFrontier.addFirst(root);
        }
        else {
            // We know nothing about this domain, so we'll start with the index, trying both HTTP and HTTPS
            crawlFrontier.addToQueue(new EdgeUrl("http", new EdgeDomain(domain), null, "/", null));
            crawlFrontier.addToQueue(new EdgeUrl("https", new EdgeDomain(domain), null, "/", null));
        }
    }

    public int fetch() {
        return fetch(new DomainLinks(), new CrawlDataReference());
    }

    public int fetch(DomainLinks domainLinks, CrawlDataReference oldCrawlData) {
        final DomainProber.ProbeResult probeResult = domainProber.probeDomain(fetcher, domain, crawlFrontier.peek());

        return switch (probeResult) {
            case DomainProber.ProbeResultOk(EdgeUrl probedUrl) -> crawlDomain(oldCrawlData, probedUrl, domainLinks);
            case DomainProber.ProbeResultError(CrawlerDomainStatus status, String desc) -> {
                crawledDomainWriter.accept(
                        CrawledDomain.builder()
                                .crawlerStatus(status.name())
                                .crawlerStatusDesc(desc)
                                .domain(domain)
                                .ip(findIp(domain))
                                .build()
                );
                yield 1;
            }
            case DomainProber.ProbeResultRedirect(EdgeDomain redirectDomain) -> {
                crawledDomainWriter.accept(
                        CrawledDomain.builder()
                                .crawlerStatus(CrawlerDomainStatus.REDIRECT.name())
                                .crawlerStatusDesc("Redirected to different domain")
                                .redirectDomain(redirectDomain.toString())
                                .domain(domain)
                                .ip(findIp(domain))
                                .build()
                );
                yield 1;
            }
        };
    }

    public void syncAbortedRun(Path warcFile) {
        var resync = new CrawlerWarcResynchronizer(crawlFrontier, warcRecorder);

        resync.run(warcFile);
    }

    private int crawlDomain(CrawlDataReference oldCrawlData, EdgeUrl rootUrl, DomainLinks domainLinks) {
        String ip = findIp(domain);

        assert !crawlFrontier.isEmpty();

        final SimpleRobotRules robotsRules = fetcher.fetchRobotRules(crawlFrontier.peek().domain, warcRecorder);
        final CrawlDelayTimer delayTimer = new CrawlDelayTimer(robotsRules.getCrawlDelay());

        sniffRootDocument(delayTimer, rootUrl);

        // Play back the old crawl data (if present) and fetch the documents comparing etags and last-modified
        int recrawled = recrawl(oldCrawlData, robotsRules, delayTimer);

        if (recrawled > 0) {
            // If we have reference data, we will always grow the crawl depth a bit
            crawlFrontier.increaseDepth(1.5);
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
                crawledDomainWriter.accept(CrawledDocumentFactory.createRobotsError(top));
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


            if (fetchWriteAndSleep(top, delayTimer, DocumentWithReference.empty()).isPresent()) {
                fetchedCount++;
            }
        }

        ret.cookies = fetcher.getCookies();

        crawledDomainWriter.accept(ret);

        return fetchedCount;
    }

    /** Using the old crawl data, fetch the documents comparing etags and last-modified */
    private int recrawl(CrawlDataReference oldCrawlData, SimpleRobotRules robotsRules, CrawlDelayTimer delayTimer) {
        return crawlerRevisitor.recrawl(oldCrawlData, robotsRules, delayTimer);
    }

    private void sniffRootDocument(CrawlDelayTimer delayTimer, EdgeUrl rootUrl) {
        try {
            logger.debug("Configuring link filter");

            var url = rootUrl.withPathAndParam("/", null);

            var maybeSample = fetchUrl(url, delayTimer, DocumentWithReference.empty()).filter(sample -> sample.httpStatus == 200);
            if (maybeSample.isEmpty())
                return;
            var sample = maybeSample.get();

            if (sample.documentBody == null)
                return;

            // Sniff the software based on the sample document
            var doc = Jsoup.parse(sample.documentBody);
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
    }

    public Optional<CrawledDocument> fetchWriteAndSleep(EdgeUrl top,
                                                         CrawlDelayTimer timer,
                                                         DocumentWithReference reference) {
        logger.debug("Fetching {}", top);

        long startTime = System.currentTimeMillis();

        var docOpt = fetchUrl(top, timer, reference);

        if (docOpt.isPresent()) {
            var doc = docOpt.get();

            if (!Objects.equals(doc.recrawlState, CrawlerRevisitor.documentWasRetainedTag)
                && reference.isContentBodySame(doc))
            {
                // The document didn't change since the last time
                doc.recrawlState = CrawlerRevisitor.documentWasSameTag;
            }

            crawledDomainWriter.accept(doc);

            if (doc.url != null) {
                // We may have redirected to a different path
                EdgeUrl.parse(doc.url).ifPresent(crawlFrontier::addVisited);
            }

            if ("ERROR".equals(doc.crawlerStatus) && doc.httpStatus != 404) {
                errorCount++;
            }

        }

        timer.delay(System.currentTimeMillis() - startTime);

        return docOpt;
    }

    private boolean isAllowedProtocol(String proto) {
        return proto.equalsIgnoreCase("http")
                || proto.equalsIgnoreCase("https");
    }

    private Optional<CrawledDocument> fetchUrl(EdgeUrl top, CrawlDelayTimer timer, DocumentWithReference reference) {
        try {
            var contentTags = reference.getContentTags();
            var fetchedDoc = tryDownload(top, timer, contentTags);

            CrawledDocument doc = reference.replaceOn304(fetchedDoc);

            if (doc.documentBody != null) {
                doc.documentBodyHash = createHash(doc.documentBody);

                var parsedDoc = Jsoup.parse(doc.documentBody);
                EdgeUrl url = new EdgeUrl(doc.url);

                crawlFrontier.enqueueLinksFromDocument(url, parsedDoc);
                findCanonicalUrl(url, parsedDoc)
                        .ifPresent(canonicalLink -> doc.canonicalUrl = canonicalLink.toString());
            }

            return Optional.of(doc);
        }
        catch (Exception ex) {
            logger.warn("Failed to process document {}", top);
        }

        return Optional.empty();

    }


    @SneakyThrows
    private CrawledDocument tryDownload(EdgeUrl top, CrawlDelayTimer timer, ContentTags tags) {
        for (int i = 0; i < 2; i++) {
            try {
                var doc = fetcher.fetchContent(top, warcRecorder, tags);
                doc.recrawlState = "NEW";
                return doc;
            }
            catch (RateLimitException ex) {
                timer.slowDown();

                int delay = ex.retryAfter();
                if (delay > 0 && delay < 5000) {
                    Thread.sleep(delay);
                }
            }
        }

        return CrawledDocumentFactory.createRetryError(top);
    }

    private String createHash(String documentBodyHash) {
        return hashMethod.hashUnencodedChars(documentBodyHash).toString();
    }

    private Optional<EdgeUrl> findCanonicalUrl(EdgeUrl baseUrl, Document parsed) {
        baseUrl = baseUrl.domain.toRootUrl();

        for (var link : parsed.select("link[rel=canonical]")) {
            return linkParser.parseLink(baseUrl, link);
        }

        return Optional.empty();
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

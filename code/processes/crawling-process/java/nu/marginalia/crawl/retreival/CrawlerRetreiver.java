package nu.marginalia.crawl.retreival;

import crawlercommons.robots.SimpleRobotRules;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.contenttype.ContentType;
import nu.marginalia.crawl.CrawlerMain;
import nu.marginalia.crawl.DomainStateDb;
import nu.marginalia.crawl.fetcher.ContentTags;
import nu.marginalia.crawl.fetcher.DomainCookies;
import nu.marginalia.crawl.fetcher.HttpFetcher;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.logic.LinkFilterSelector;
import nu.marginalia.crawl.retreival.revisit.CrawlerRevisitor;
import nu.marginalia.crawl.retreival.revisit.DocumentWithReference;
import nu.marginalia.ip_blocklist.UrlBlocklist;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.DocumentBodyExtractor;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.model.crawldata.CrawlerDomainStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CrawlerRetreiver implements AutoCloseable {

    private static final int MAX_ERRORS = 20;

    private final HttpFetcher fetcher;

    private final String domain;

    private static final LinkParser linkParser = new LinkParser();
    private static final Logger logger = LoggerFactory.getLogger(CrawlerRetreiver.class);

    private static final UrlBlocklist urlBlocklist = new UrlBlocklist();
    private static final LinkFilterSelector linkFilterSelector = new LinkFilterSelector();

    private final DomainProber domainProber;
    private final DomainCrawlFrontier crawlFrontier;
    private final DomainStateDb domainStateDb;
    private final WarcRecorder warcRecorder;
    private final CrawlerRevisitor crawlerRevisitor;
    private final DomainCookies cookies = new DomainCookies();

    private static final CrawlerConnectionThrottle connectionThrottle = new CrawlerConnectionThrottle(
            Duration.ofSeconds(1) // pace the connections to avoid network congestion at startup
    );

    int errorCount = 0;

    public CrawlerRetreiver(HttpFetcher fetcher,
                            DomainProber domainProber,
                            CrawlerMain.CrawlSpecRecord specs,
                            DomainStateDb domainStateDb,
                            WarcRecorder warcRecorder)
    {
        this.domainStateDb = domainStateDb;
        this.warcRecorder = warcRecorder;
        this.fetcher = fetcher;
        this.domainProber = domainProber;

        domain = specs.domain();

        crawlFrontier = new DomainCrawlFrontier(new EdgeDomain(domain), specs.urls(), specs.crawlDepth());
        crawlerRevisitor = new CrawlerRevisitor(crawlFrontier, this, warcRecorder);

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

    public int crawlDomain() {
        return crawlDomain(new DomainLinks(), new CrawlDataReference());
    }

    public int crawlDomain(DomainLinks domainLinks, CrawlDataReference oldCrawlData) {
        try (oldCrawlData) {

            // Wait for permission to open a connection to avoid network congestion
            // from hundreds/thousands of TCP handshakes
            connectionThrottle.waitForConnectionPermission();

            // Do an initial domain probe to determine the root URL
            var probeResult = probeRootUrl();

            return switch (probeResult) {
                case HttpFetcher.DomainProbeResult.Ok(EdgeUrl probedUrl) -> {

                    // Sleep after the initial probe, we don't have access to the robots.txt yet
                    // so we don't know the crawl delay
                    TimeUnit.SECONDS.sleep(1);

                    final SimpleRobotRules robotsRules = fetcher.fetchRobotRules(probedUrl.domain, warcRecorder);
                    final CrawlDelayTimer delayTimer = new CrawlDelayTimer(robotsRules.getCrawlDelay());

                    delayTimer.waitFetchDelay(0); // initial delay after robots.txt

                    DomainStateDb.SummaryRecord summaryRecord = sniffRootDocument(probedUrl, delayTimer);
                    domainStateDb.save(summaryRecord);

                    if (Thread.interrupted()) {
                        // There's a small chance we're interrupted during the sniffing portion
                        throw new InterruptedException();
                    }

                    Instant recrawlStart = Instant.now();
                    CrawlerRevisitor.RecrawlMetadata recrawlMetadata = crawlerRevisitor.recrawl(oldCrawlData, cookies, robotsRules, delayTimer);
                    Duration recrawlTime = Duration.between(recrawlStart, Instant.now());

                    // Play back the old crawl data (if present) and fetch the documents comparing etags and last-modified
                    if (recrawlMetadata.size() > 0) {
                        // If we have reference data, we will always grow the crawl depth a bit
                        crawlFrontier.increaseDepth(1.5, 2500);
                    }

                    oldCrawlData.close(); // proactively close the crawl data reference here to not hold onto expensive resources

                    yield crawlDomain(probedUrl, robotsRules, delayTimer, domainLinks, recrawlMetadata, recrawlTime);
                }
                case HttpFetcher.DomainProbeResult.Redirect(EdgeDomain domain1) -> {
                    domainStateDb.save(DomainStateDb.SummaryRecord.forError(domain, "Redirect", domain1.toString()));
                    yield 1;
                }
                case HttpFetcher.DomainProbeResult.Error(CrawlerDomainStatus status, String desc) -> {
                    domainStateDb.save(DomainStateDb.SummaryRecord.forError(domain, status.toString(), desc));
                    yield 1;
                }
                default -> {
                    logger.error("Unexpected domain probe result {}", probeResult);
                    yield 1;
                }
            };

        }
        catch (Exception ex) {
            logger.error("Error crawling domain {}", domain, ex);
            return 0;
        }
    }

    private int crawlDomain(EdgeUrl rootUrl,
                            SimpleRobotRules robotsRules,
                            CrawlDelayTimer delayTimer,
                            DomainLinks domainLinks,
                            CrawlerRevisitor.RecrawlMetadata recrawlMetadata,
                            Duration recrawlTime) {

        Instant crawlStart = Instant.now();

        // Add external links to the crawl frontier
        crawlFrontier.addAllToQueue(domainLinks.getUrls(rootUrl.proto));

        // Fetch sitemaps
        for (var sitemap : robotsRules.getSitemaps()) {

            // Validate the sitemap URL and check if it belongs to the domain as the root URL
            if (EdgeUrl.parse(sitemap)
                    .map(url -> url.getDomain().equals(rootUrl.domain))
                    .orElse(false)) {

                crawlFrontier.addAllToQueue(fetcher.fetchSitemapUrls(sitemap, delayTimer));
            }
        }

        int crawlerAdditions = 0;

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
                var result = fetchContentWithReference(top, delayTimer, DocumentWithReference.empty());

                if (result.isOk()) {
                    crawlerAdditions++;
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        Duration crawlTime = Duration.between(crawlStart, Instant.now());
        domainStateDb.save(new DomainStateDb.CrawlMeta(
                domain,
                Instant.now(),
                recrawlTime,
                crawlTime,
                recrawlMetadata.errors(),
                crawlerAdditions,
                recrawlMetadata.size() + crawlerAdditions
        ));

        return crawlFrontier.visitedSize();
    }

    public void syncAbortedRun(Path warcFile) {
        var resync = new CrawlerWarcResynchronizer(crawlFrontier, warcRecorder);

        resync.run(warcFile);
    }

    private HttpFetcher.DomainProbeResult probeRootUrl() throws IOException {
        // Construct an URL to the root of the domain, we don't know the schema yet so we'll
        // start with http and then try https if that fails
        var httpUrl = new EdgeUrl("https", new EdgeDomain(domain), null, "/", null);
        final HttpFetcher.DomainProbeResult domainProbeResult = domainProber.probeDomain(fetcher, domain, httpUrl);

        String ip;
        try {
            ip = InetAddress.getByName(domain).getHostAddress();
        } catch (UnknownHostException e) {
            ip = "";
        }

        // Write the domain probe result to the WARC file
        warcRecorder.writeWarcinfoHeader(ip, new EdgeDomain(domain), domainProbeResult);

        return domainProbeResult;
    }



    private DomainStateDb.SummaryRecord sniffRootDocument(EdgeUrl rootUrl, CrawlDelayTimer timer) {
        Optional<String> feedLink = Optional.empty();

        try {
            var url = rootUrl.withPathAndParam("/", null);

            HttpFetchResult result = fetcher.fetchContent(url, warcRecorder, cookies, timer, ContentTags.empty(), HttpFetcher.ProbeType.DISABLED);
            timer.waitFetchDelay(0);

            if (result instanceof HttpFetchResult.ResultRedirect(EdgeUrl location)) {
                if (Objects.equals(location.domain, url.domain)) {
                    // TODO: Follow the redirect to the new location and sniff the document
                    crawlFrontier.addFirst(location);
                }

                return DomainStateDb.SummaryRecord.forSuccess(domain);
            }

            if (!(result instanceof HttpFetchResult.ResultOk ok)) {
                return DomainStateDb.SummaryRecord.forSuccess(domain);
            }

            var optDoc = ok.parseDocument();
            if (optDoc.isEmpty())
                return DomainStateDb.SummaryRecord.forSuccess(domain);

            // Sniff the software based on the sample document
            var doc = optDoc.get();
            crawlFrontier.setLinkFilter(linkFilterSelector.selectFilter(doc));
            crawlFrontier.enqueueLinksFromDocument(url, doc);

            EdgeUrl faviconUrl = url.withPathAndParam("/favicon.ico", null);

            for (var link : doc.getElementsByTag("link")) {
                String rel = link.attr("rel");
                String type = link.attr("type");

                if (rel.equals("icon") || rel.equals("shortcut icon")) {
                    String href = link.attr("href");

                    faviconUrl = linkParser.parseLink(url, href)
                            .filter(crawlFrontier::isSameDomain)
                            .orElse(faviconUrl);
                }

                // Grab the RSS/Atom as a sitemap if it exists
                if (rel.equalsIgnoreCase("alternate")
                && (type.equalsIgnoreCase("application/atom+xml")
                        || type.equalsIgnoreCase("application/atomsvc+xml")
                        || type.equalsIgnoreCase("application/rss+xml")
                )) {
                    String href = link.attr("href");

                    feedLink = linkParser.parseLink(url, href)
                            .filter(crawlFrontier::isSameDomain)
                            .map(EdgeUrl::toString);
                }
            }


            if (feedLink.isEmpty()) {
                feedLink = guessFeedUrl(timer);
            }

            // Download the sitemap if available
            feedLink.ifPresent(s -> fetcher.fetchSitemapUrls(s, timer));

            // Grab the favicon if it exists

            if (fetcher.fetchContent(faviconUrl, warcRecorder, cookies, timer, ContentTags.empty(), HttpFetcher.ProbeType.DISABLED) instanceof HttpFetchResult.ResultOk iconResult) {
                String contentType = iconResult.header("Content-Type");
                byte[] iconData = iconResult.getBodyBytes();

                domainStateDb.saveIcon(
                        domain,
                        new DomainStateDb.FaviconRecord(contentType, iconData)
                );
            }
            timer.waitFetchDelay(0);

        }
        catch (Exception ex) {
            logger.error("Error configuring link filter", ex);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                return DomainStateDb.SummaryRecord.forError(domain, "Crawler Interrupted", ex.getMessage());
            }
        }
        finally {
            crawlFrontier.addVisited(rootUrl);
        }

        if (feedLink.isPresent()) {
            return DomainStateDb.SummaryRecord.forSuccess(domain, feedLink.get());
        }
        else {
            return DomainStateDb.SummaryRecord.forSuccess(domain);
        }
    }

    private final List<String> likelyFeedEndpoints = List.of(
            "rss.xml",
            "atom.xml",
            "feed.xml",
            "index.xml",
            "feed",
            "rss",
            "atom",
            "feeds",
            "blog/feed",
            "blog/rss"
    );

    private Optional<String> guessFeedUrl(CrawlDelayTimer timer) throws InterruptedException {
        var oldDomainStateRecord = domainStateDb.getSummary(domain);

        // If we are already aware of an old feed URL, then we can just revalidate it
        if (oldDomainStateRecord.isPresent()) {
            var oldRecord = oldDomainStateRecord.get();
            if (oldRecord.feedUrl() != null && validateFeedUrl(oldRecord.feedUrl(), timer)) {
                return Optional.of(oldRecord.feedUrl());
            }
        }

        for (String endpoint : likelyFeedEndpoints) {
            String url = "https://" + domain + "/" + endpoint;
            if (validateFeedUrl(url, timer)) {
                return Optional.of(url);
            }
        }

        return Optional.empty();
    }

    private boolean validateFeedUrl(String url, CrawlDelayTimer timer) throws InterruptedException {
        var parsedOpt = EdgeUrl.parse(url);
        if (parsedOpt.isEmpty())
            return false;

        HttpFetchResult result = fetcher.fetchContent(parsedOpt.get(), warcRecorder, cookies, timer, ContentTags.empty(), HttpFetcher.ProbeType.DISABLED);
        timer.waitFetchDelay(0);

        if (!(result instanceof HttpFetchResult.ResultOk ok)) {
            return false;
        }

        // Extract the beginning of the
        Optional<String> bodyOpt = DocumentBodyExtractor.asString(ok).getBody();
        if (bodyOpt.isEmpty())
            return false;
        String body = bodyOpt.get();
        body = body.substring(0, Math.min(128, body.length())).toLowerCase();

        if (body.contains("<atom"))
            return true;
        if (body.contains("<rss"))
            return true;

        return false;
    }

    public HttpFetchResult fetchContentWithReference(EdgeUrl top,
                                                     CrawlDelayTimer timer,
                                                     DocumentWithReference reference) throws InterruptedException
    {
        var contentTags = reference.getContentTags();

        HttpFetchResult fetchedDoc = fetcher.fetchContent(top, warcRecorder, cookies, timer, contentTags, HttpFetcher.ProbeType.FULL);
        timer.waitFetchDelay();

        if (Thread.interrupted()) {
            Thread.currentThread().interrupt();
            throw new InterruptedException();
        }

        // Parse the document and enqueue links
        try {
            switch (fetchedDoc) {
                case HttpFetchResult.ResultOk ok -> {
                    var docOpt = ok.parseDocument();
                    if (docOpt.isPresent()) {
                        var doc = docOpt.get();

                        var responseUrl = new EdgeUrl(ok.uri());

                        crawlFrontier.enqueueLinksFromDocument(responseUrl, doc);
                        crawlFrontier.addVisited(responseUrl);
                    }
                }
                case HttpFetchResult.Result304Raw ref when reference.doc() != null ->
                {
                    var doc = reference.doc();

                    warcRecorder.writeReferenceCopy(top, cookies, doc.contentType, doc.httpStatus, doc.documentBodyBytes, doc.headers, contentTags);

                    fetchedDoc = new HttpFetchResult.Result304ReplacedWithReference(doc.url,
                            new ContentType(doc.contentType, "UTF-8"),
                            doc.documentBodyBytes);

                    if (doc.documentBodyBytes != null) {
                        var parsed = doc.parseBody();

                        crawlFrontier.enqueueLinksFromDocument(top, parsed);
                        crawlFrontier.addVisited(top);
                    }
                }
                case HttpFetchResult.ResultRedirect(EdgeUrl location) -> {
                    if (Objects.equals(location.domain, top.domain)) {
                        crawlFrontier.addFirst(location);
                    }
                }
                case HttpFetchResult.ResultException ex -> errorCount++;
                default -> {} // Ignore other types
            }
        }
        catch (Exception ex) {
            logger.error("Error parsing document {}", top, ex);
        }

        return fetchedDoc;
    }

    private boolean isAllowedProtocol(String proto) {
        return proto.equalsIgnoreCase("http")
                || proto.equalsIgnoreCase("https");
    }

    @Override
    public void close() throws Exception {
        warcRecorder.close();
    }

}

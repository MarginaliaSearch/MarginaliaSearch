package nu.marginalia.crawl.retreival;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import crawlercommons.robots.SimpleRobotRules;
import lombok.SneakyThrows;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawl.retreival.fetcher.SitemapRetriever;
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

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

public class CrawlerRetreiver {

    private static final int MAX_ERRORS = 20;

    private final HttpFetcher fetcher;

    private final String domain;
    private final Consumer<SerializableCrawlData> crawledDomainWriter;

    private static final LinkParser linkParser = new LinkParser();
    private static final Logger logger = LoggerFactory.getLogger(CrawlerRetreiver.class);

    private static final HashFunction hashMethod = Hashing.murmur3_128(0);
    private static final UrlBlocklist urlBlocklist = new UrlBlocklist();
    private static final LinkFilterSelector linkFilterSelector = new LinkFilterSelector();

    private static final DomainProber domainProber = new DomainProber();
    private final SitemapRetriever sitemapRetriever;
    private final DomainCrawlFrontier crawlFrontier;

    int errorCount = 0;

    /** recrawlState tag for documents that had a HTTP status 304 */
    private static final String documentWasRetainedTag = "RETAINED/304";

    /** recrawlState tag for documents that had a 200 status but were identical to a previous version */
    private static final String documentWasSameTag = "SAME-BY-COMPARISON";

    public CrawlerRetreiver(HttpFetcher fetcher,
                            CrawlSpecRecord specs,
                            Consumer<SerializableCrawlData> writer) {
        this.fetcher = fetcher;

        domain = specs.domain;

        crawledDomainWriter = writer;

        this.crawlFrontier = new DomainCrawlFrontier(new EdgeDomain(domain), Objects.requireNonNullElse(specs.urls, List.of()), specs.crawlDepth);
        sitemapRetriever = fetcher.createSitemapRetriever();

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

    private int crawlDomain(CrawlDataReference oldCrawlData, EdgeUrl rootUrl, DomainLinks domainLinks) {
        String ip = findIp(domain);

        assert !crawlFrontier.isEmpty();

        final SimpleRobotRules robotsRules = fetcher.fetchRobotRules(crawlFrontier.peek().domain);
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
        downloadSitemaps(robotsRules, rootUrl);

        CrawledDomain ret = new CrawledDomain(domain, null, CrawlerDomainStatus.OK.name(), null, ip, new ArrayList<>(), null);

        int fetchedCount = recrawled;

        while (!crawlFrontier.isEmpty()
            && !crawlFrontier.isCrawlDepthReached()
            && errorCount < MAX_ERRORS
            && !Thread.interrupted())
        {
            var top = crawlFrontier.takeNextUrl();

            if (!robotsRules.isAllowed(top.toString())) {
                crawledDomainWriter.accept(createRobotsError(top));
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

    /** Performs a re-crawl of old documents, comparing etags and last-modified */
    private int recrawl(CrawlDataReference oldCrawlData,
                        SimpleRobotRules robotsRules,
                        CrawlDelayTimer delayTimer) {
        int recrawled = 0;
        int retained = 0;

        for (;;) {
            CrawledDocument doc = oldCrawlData.nextDocument();

            if (doc == null) {
                break;
            }

            // This Shouldn't Happen (TM)
            var urlMaybe = EdgeUrl.parse(doc.url);
            if (urlMaybe.isEmpty()) continue;
            var url = urlMaybe.get();

            // If we've previously 404:d on this URL, we'll refrain from trying to fetch it again
            if (doc.httpStatus == 404) {
                crawlFrontier.addVisited(url);
                continue;
            }

            if (doc.httpStatus != 200) continue;

            if (!robotsRules.isAllowed(url.toString())) {
                crawledDomainWriter.accept(createRobotsError(url));
                continue;
            }
            if (!crawlFrontier.filterLink(url))
                continue;
            if (!crawlFrontier.addVisited(url))
                continue;


            if (recrawled > 5
             && retained > 0.9 * recrawled
             && Math.random() < 0.9)
            {
                // Since it looks like most of these documents haven't changed,
                // we'll load the documents directly; but we do this in a random
                // fashion to make sure we eventually catch changes over time

                crawledDomainWriter.accept(doc);
                crawlFrontier.addVisited(url);
                continue;
            }


            // GET the document with the stored document as a reference
            // providing etag and last-modified headers, so we can recycle the
            // document if it hasn't changed without actually downloading it

            var fetchedDocOpt = fetchWriteAndSleep(url,
                    delayTimer,
                    new DocumentWithReference(doc, oldCrawlData));
            if (fetchedDocOpt.isEmpty()) continue;

            if (documentWasRetainedTag.equals(fetchedDocOpt.get().recrawlState)) retained ++;
            else if (documentWasSameTag.equals(fetchedDocOpt.get().recrawlState)) retained ++;

            recrawled ++;
        }

        return recrawled;
    }

    private void downloadSitemaps(SimpleRobotRules robotsRules, EdgeUrl rootUrl) {
        List<String> sitemaps = robotsRules.getSitemaps();

        List<EdgeUrl> urls = new ArrayList<>(sitemaps.size());
        if (!sitemaps.isEmpty()) {
            for (var url : sitemaps) {
                EdgeUrl.parse(url).ifPresent(urls::add);
            }
        }
        else {
            urls.add(rootUrl.withPathAndParam("/sitemap.xml", null));
        }

        downloadSitemaps(urls);
    }

    private void downloadSitemaps(List<EdgeUrl> urls) {

        Set<String> checkedSitemaps = new HashSet<>();

        for (var url : urls) {
            // Let's not download sitemaps from other domains for now
            if (!crawlFrontier.isSameDomain(url)) {
                continue;
            }

            if (checkedSitemaps.contains(url.path))
                continue;

            var sitemap =  sitemapRetriever.fetchSitemap(url);
            if (sitemap.isEmpty()) {
                continue;
            }

            // ensure we don't try to download this sitemap again
            // (don't move this up, as we may want to check the same
            // path with different protocols until we find one that works)

            checkedSitemaps.add(url.path);

            crawlFrontier.addAllToQueue(sitemap);
        }

        logger.debug("Queue is now {}", crawlFrontier.queueSize());
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
                        .ifPresent(this::downloadSitemaps);
            }
        }
        catch (Exception ex) {
            logger.error("Error configuring link filter", ex);
        }
    }

    private Optional<CrawledDocument> fetchWriteAndSleep(EdgeUrl top,
                                                         CrawlDelayTimer timer,
                                                         DocumentWithReference reference) {
        logger.debug("Fetching {}", top);

        long startTime = System.currentTimeMillis();

        var docOpt = fetchUrl(top, timer, reference);

        if (docOpt.isPresent()) {
            var doc = docOpt.get();

            if (!Objects.equals(doc.recrawlState, documentWasRetainedTag)
                && reference.isContentBodySame(doc))
            {
                // The document didn't change since the last time
                doc.recrawlState = documentWasSameTag;
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

                findLinks(url, parsedDoc);
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
                var doc = fetcher.fetchContent(top, tags);
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

        return createRetryError(top);
    }

    private String createHash(String documentBodyHash) {
        return hashMethod.hashUnencodedChars(documentBodyHash).toString();
    }

    private void findLinks(EdgeUrl baseUrl, Document parsed) {
        baseUrl = linkParser.getBaseLink(parsed, baseUrl);

        for (var link : parsed.getElementsByTag("a")) {
            linkParser.parseLink(baseUrl, link).ifPresent(crawlFrontier::addToQueue);
        }
        for (var link : parsed.getElementsByTag("frame")) {
            linkParser.parseFrame(baseUrl, link).ifPresent(crawlFrontier::addToQueue);
        }
        for (var link : parsed.getElementsByTag("iframe")) {
            linkParser.parseFrame(baseUrl, link).ifPresent(crawlFrontier::addToQueue);
        }
        for (var link : parsed.getElementsByTag("link")) {
            String rel = link.attr("rel");

            if (rel.equalsIgnoreCase("next") || rel.equalsIgnoreCase("prev")) {
                linkParser.parseLink(baseUrl, link).ifPresent(crawlFrontier::addToQueue);
            }
        }
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

    private CrawledDocument createRobotsError(EdgeUrl url) {
        return CrawledDocument.builder()
                .url(url.toString())
                .timestamp(LocalDateTime.now().toString())
                .httpStatus(-1)
                .crawlerStatus(CrawlerDocumentStatus.ROBOTS_TXT.name())
                .build();
    }
    private CrawledDocument createRetryError(EdgeUrl url) {
        return CrawledDocument.builder()
                .url(url.toString())
                .timestamp(LocalDateTime.now().toString())
                .httpStatus(429)
                .crawlerStatus(CrawlerDocumentStatus.ERROR.name())
                .build();
    }

    private record DocumentWithReference(
            @Nullable CrawledDocument doc,
            @Nullable CrawlDataReference reference) {

        private static final DocumentWithReference emptyInstance = new DocumentWithReference(null, null);
        public static DocumentWithReference empty() {
            return emptyInstance;
        }

        public boolean isContentBodySame(CrawledDocument newDoc) {
            if (reference == null)
                return false;
            if (doc == null)
                return false;
            if (doc.documentBody == null)
                return false;
            if (newDoc.documentBody == null)
                return false;

            return reference.isContentBodySame(doc, newDoc);
        }

        private ContentTags getContentTags() {
            if (null == doc)
                return ContentTags.empty();

            String headers = doc.headers;
            if (headers == null)
                return ContentTags.empty();

            String[] headersLines = headers.split("\n");

            String lastmod = null;
            String etag = null;

            for (String line : headersLines) {
                if (line.toLowerCase().startsWith("etag:")) {
                    etag = line.substring(5).trim();
                }
                if (line.toLowerCase().startsWith("last-modified:")) {
                    lastmod = line.substring(14).trim();
                }
            }

            return new ContentTags(etag, lastmod);
        }

        public boolean isEmpty() {
            return doc == null || reference == null;
        }

        /** If the provided document has HTTP status 304, and the reference document is provided,
         *  return the reference document; otherwise return the provided document.
         */
        public CrawledDocument replaceOn304(CrawledDocument fetchedDoc) {

            if (doc == null)
                return fetchedDoc;

            // HTTP status 304 is NOT MODIFIED, which means the document is the same as it was when
            // we fetched it last time. We can recycle the reference document.
            if (fetchedDoc.httpStatus != 304)
                return fetchedDoc;

            var ret = doc;
            ret.recrawlState = documentWasRetainedTag;
            ret.timestamp = LocalDateTime.now().toString();
            return ret;
        }
    }

}

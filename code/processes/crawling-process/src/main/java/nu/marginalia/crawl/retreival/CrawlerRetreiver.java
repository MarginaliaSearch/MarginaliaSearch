package nu.marginalia.crawl.retreival;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import crawlercommons.robots.SimpleRobotRules;
import lombok.SneakyThrows;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawl.retreival.fetcher.SitemapRetriever;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.crawling.model.*;
import nu.marginalia.ip_blocklist.UrlBlocklist;
import nu.marginalia.lsh.EasyLSH;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
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

import static java.lang.Math.max;
import static java.lang.Math.min;

public class CrawlerRetreiver {
    private static final long DEFAULT_CRAWL_DELAY_MIN_MS = Long.getLong("defaultCrawlDelay", 1000);
    private static final long DEFAULT_CRAWL_DELAY_MAX_MS = Long.getLong("defaultCrawlDelaySlow", 2500);

    private static final int MAX_ERRORS = 20;

    private final HttpFetcher fetcher;


    /** Flag to indicate that the crawler should slow down, e.g. from 429s */
    private boolean slowDown = false;


    /** Testing flag to disable crawl delay (otherwise crawler tests take several minutes) */
    private boolean testFlagIgnoreDelay = false;

    private final String id;
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
    private String retainedTag = "RETAINED/304";

    public CrawlerRetreiver(HttpFetcher fetcher,
                            CrawlingSpecification specs,
                            Consumer<SerializableCrawlData> writer) {
        this.fetcher = fetcher;

        id = specs.id;
        domain = specs.domain;

        crawledDomainWriter = writer;

        this.crawlFrontier = new DomainCrawlFrontier(new EdgeDomain(domain), specs.urls, specs.crawlDepth);
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

    public CrawlerRetreiver withNoDelay() {
        testFlagIgnoreDelay = true;
        return this;
    }

    public int fetch() {
        return fetch(new CrawlDataReference());
    }

    public int fetch(CrawlDataReference oldCrawlData) {
        final DomainProber.ProbeResult probeResult = domainProber.probeDomain(fetcher, domain, crawlFrontier.peek());

        if (probeResult instanceof DomainProber.ProbeResultOk) {
            return crawlDomain(oldCrawlData);
        }

        // handle error cases for probe

        var ip = findIp(domain);

        if (probeResult instanceof DomainProber.ProbeResultError err) {
            crawledDomainWriter.accept(
                    CrawledDomain.builder()
                            .crawlerStatus(err.status().name())
                            .crawlerStatusDesc(err.desc())
                            .id(id)
                            .domain(domain)
                            .ip(ip)
                            .build()
            );
            return 1;
        }

        if (probeResult instanceof DomainProber.ProbeResultRedirect redirect) {
            crawledDomainWriter.accept(
                    CrawledDomain.builder()
                            .crawlerStatus(CrawlerDomainStatus.REDIRECT.name())
                            .crawlerStatusDesc("Redirected to different domain")
                            .redirectDomain(redirect.domain().toString())
                            .id(id)
                            .domain(domain)
                            .ip(ip)
                            .build()
            );
            return 1;
        }

        throw new IllegalStateException("Unknown probe result: " + probeResult);
    };

    private int crawlDomain(CrawlDataReference oldCrawlData) {
        String ip = findIp(domain);

        assert !crawlFrontier.isEmpty();

        var robotsRules = fetcher.fetchRobotRules(crawlFrontier.peek().domain);
        long crawlDelay = robotsRules.getCrawlDelay();

        sniffRootDocument();

        // Play back the old crawl data (if present) and fetch the documents comparing etags and last-modified
        int recrawled = recrawl(oldCrawlData, robotsRules, crawlDelay);

        if (recrawled > 0) {
            // If we have reference data, we will always grow the crawl depth a bit
            crawlFrontier.increaseDepth(1.5);
        }

        downloadSitemaps(robotsRules);

        CrawledDomain ret = new CrawledDomain(id, domain, null, CrawlerDomainStatus.OK.name(), null, ip, new ArrayList<>(), null);

        int fetchedCount = recrawled;

        while (!crawlFrontier.isEmpty()
            && !crawlFrontier.isCrawlDepthReached()
            && errorCount < MAX_ERRORS)
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


            if (fetchDocument(top, null, crawlDelay).isPresent()) {
                fetchedCount++;
            }
        }

        ret.cookies = fetcher.getCookies();

        crawledDomainWriter.accept(ret);

        return fetchedCount;
    }

    private int recrawl(CrawlDataReference oldCrawlData,
                        SimpleRobotRules robotsRules,
                        long crawlDelay) {
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


            if (recrawled > 10
             && retained > 0.9 * recrawled
             && Math.random() < 0.75)
            {
                logger.info("Direct-loading {}", url);

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

            var fetchedDocOpt = fetchDocument(url, doc, crawlDelay);
            if (fetchedDocOpt.isEmpty()) continue;

            if (Objects.equals(fetchedDocOpt.get().recrawlState, retainedTag)) {
                retained ++;
            }
            else if (oldCrawlData.isContentSame(doc, fetchedDocOpt.get())) {
                retained ++;
            }

            recrawled ++;
        }

        return recrawled;
    }

    private static final HashFunction hasher = Hashing.murmur3_128(0);
    private long hashDoc(CrawledDocument doc) {
        var hash = new EasyLSH();
        long val = 0;
        for (var b : doc.documentBody.decode().getBytes()) {
            val = val << 8 | (b & 0xFF);
            hash.addUnordered(hasher.hashLong(val).asLong());
        }
        return hash.get();
    }


    private void downloadSitemaps(SimpleRobotRules robotsRules) {
        List<String> sitemaps = robotsRules.getSitemaps();
        if (sitemaps.isEmpty()) {
            sitemaps = List.of(
                    "http://" + domain + "/sitemap.xml",
                    "https://" + domain + "/sitemap.xml");
        }

        List<EdgeUrl> urls = new ArrayList<>(sitemaps.size());
        for (var url : sitemaps) {
            EdgeUrl.parse(url).ifPresent(urls::add);
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

    private void sniffRootDocument() {
        try {
            logger.debug("Configuring link filter");

            var url = crawlFrontier.peek().withPathAndParam("/", null);

            var maybeSample = fetchUrl(url, null).filter(sample -> sample.httpStatus == 200);
            if (maybeSample.isEmpty())
                return;
            var sample = maybeSample.get();

            if (sample.documentBody == null)
                return;

            // Sniff the software based on the sample document
            var doc = Jsoup.parse(sample.documentBody.decode());
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

    private Optional<CrawledDocument> fetchDocument(EdgeUrl top,
                                                    @Nullable CrawledDocument reference,
                                                    long crawlDelay) {
        logger.debug("Fetching {}", top);

        long startTime = System.currentTimeMillis();

        var doc = fetchUrl(top, reference);
        if (doc.isPresent()) {
            var d = doc.get();
            crawledDomainWriter.accept(d);

            if (d.url != null) {
                // We may have redirected to a different path
                EdgeUrl.parse(d.url).ifPresent(crawlFrontier::addVisited);
            }

            if ("ERROR".equals(d.crawlerStatus) && d.httpStatus != 404) {
                errorCount++;
            }

        }

        long crawledTime = System.currentTimeMillis() - startTime;
        delay(crawlDelay, crawledTime);

        return doc;
    }

    private boolean isAllowedProtocol(String proto) {
        return proto.equalsIgnoreCase("http")
                || proto.equalsIgnoreCase("https");
    }

    private Optional<CrawledDocument> fetchUrl(EdgeUrl top, @Nullable CrawledDocument reference) {
        try {
            var contentTags = getContentTags(reference);
            var fetchedDoc = fetchContent(top, contentTags);
            CrawledDocument doc;

            // HTTP status 304 is NOT MODIFIED, which means the document is the same as it was when
            // we fetched it last time. We can recycle the reference document.
            if (reference != null
             && fetchedDoc.httpStatus == 304)
            {
                doc = reference;
                doc.recrawlState = retainedTag;
                doc.timestamp = LocalDateTime.now().toString();
            }
            else {
                doc = fetchedDoc;
            }

            if (doc.documentBody != null) {
                var decoded = doc.documentBody.decode();

                doc.documentBodyHash = createHash(decoded);

                Optional<Document> parsedDoc = parseDoc(decoded);
                EdgeUrl url = new EdgeUrl(doc.url);

                parsedDoc.ifPresent(parsed -> findLinks(url, parsed));
                parsedDoc.flatMap(parsed -> findCanonicalUrl(url, parsed))
                        .ifPresent(canonicalLink -> doc.canonicalUrl = canonicalLink.toString());
            }

            return Optional.of(doc);
        }
        catch (Exception ex) {
            logger.warn("Failed to process document {}", top);
        }

        return Optional.empty();

    }

    private ContentTags getContentTags(@Nullable CrawledDocument reference) {
        if (null == reference)
            return ContentTags.empty();

        String headers = reference.headers;
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

    @SneakyThrows
    private CrawledDocument fetchContent(EdgeUrl top, ContentTags tags) {
        for (int i = 0; i < 2; i++) {
            try {
                var doc = fetcher.fetchContent(top, tags);
                doc.recrawlState = "NEW";
                return doc;
            }
            catch (RateLimitException ex) {
                slowDown = true;
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

    private Optional<Document> parseDoc(String decoded) {
        return Optional.of(Jsoup.parse(decoded));
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

    @SneakyThrows
    private void delay(long sleepTime, long spentTime) {
        if (testFlagIgnoreDelay)
            return;

        if (sleepTime >= 1) {
            if (spentTime > sleepTime)
                return;

            Thread.sleep(min(sleepTime - spentTime, 5000));
        }
        else if (slowDown) {
            Thread.sleep( 1000);
        }
        else {
            // When no crawl delay is specified, lean toward twice the fetch+process time,
            // within sane limits. This means slower servers get slower crawling, and faster
            // servers get faster crawling.

            sleepTime = spentTime * 2;
            sleepTime = min(sleepTime, DEFAULT_CRAWL_DELAY_MAX_MS);
            sleepTime = max(sleepTime, DEFAULT_CRAWL_DELAY_MIN_MS);

            if (spentTime > sleepTime)
                return;

            Thread.sleep(sleepTime - spentTime);
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


    enum CrawlDataComparison {
        NO_OLD_DATA,
        SMALL_SAMPLE,
        CHANGES_FOUND,
        NO_CHANGES
    };

}

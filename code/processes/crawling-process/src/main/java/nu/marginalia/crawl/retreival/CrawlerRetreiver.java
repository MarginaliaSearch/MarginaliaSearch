package nu.marginalia.crawl.retreival;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import crawlercommons.robots.SimpleRobotRules;
import lombok.SneakyThrows;
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

    private final CrawlDataReference oldCrawlData;

    int errorCount = 0;

    public CrawlerRetreiver(HttpFetcher fetcher,
                            CrawlingSpecification specs,
                            Consumer<SerializableCrawlData> writer) {
        this.fetcher = fetcher;
        this.oldCrawlData = new CrawlDataReference(specs.oldData);

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
        final DomainProber.ProbeResult probeResult = domainProber.probeDomain(fetcher, domain, crawlFrontier.peek());

        if (probeResult instanceof DomainProber.ProbeResultOk) {
            return crawlDomain();
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

    private int crawlDomain() {
        String ip = findIp(domain);

        assert !crawlFrontier.isEmpty();

        var robotsRules = fetcher.fetchRobotRules(crawlFrontier.peek().domain);

        CrawlDataComparison comparison = compareWithOldData(robotsRules);
        logger.info("Comparison result for {} : {}", domain, comparison);

        // If we have reference data, we will always grow the crawl depth a bit
        if (oldCrawlData.size() > 0) {
            crawlFrontier.increaseDepth(1.5);
        }

        // When the reference data doesn't appear to have changed, we'll forego
        // re-fetching it and just use the old data
        if (comparison == CrawlDataComparison.NO_CHANGES) {
            oldCrawlData.allDocuments().forEach((url, doc) -> {
                if (crawlFrontier.addVisited(url)) {
                    doc.recrawlState = "RETAINED";
                    crawledDomainWriter.accept(doc);
                }
            });

            // We don't need to hold onto this in RAM anymore
            oldCrawlData.evict();
        }


        downloadSitemaps(robotsRules);
        sniffRootDocument();

        long crawlDelay = robotsRules.getCrawlDelay();

        CrawledDomain ret = new CrawledDomain(id, domain, null, CrawlerDomainStatus.OK.name(), null, ip, new ArrayList<>(), null);

        int fetchedCount = 0;

        while (!crawlFrontier.isEmpty()
            && !crawlFrontier.isCrawlDepthReached()
            && errorCount < MAX_ERRORS)
        {
            var top = crawlFrontier.takeNextUrl();

            if (!robotsRules.isAllowed(top.toString())) {
                crawledDomainWriter.accept(createRobotsError(top));
                continue;
            }

            // Don't re-fetch links that were previously found dead as it's very unlikely that a
            // 404:ing link will suddenly start working at a later point
            if (oldCrawlData.isPreviouslyDead(top))
                continue;

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


            if (fetchDocument(top, crawlDelay).isPresent()) {
                fetchedCount++;
            }
        }

        ret.cookies = fetcher.getCookies();

        crawledDomainWriter.accept(ret);

        return fetchedCount;
    }

    private CrawlDataComparison compareWithOldData(SimpleRobotRules robotsRules) {

        int numGoodDocuments = oldCrawlData.size();

        if (numGoodDocuments == 0)
            return CrawlDataComparison.NO_OLD_DATA;

        if (numGoodDocuments < 10)
            return CrawlDataComparison.SMALL_SAMPLE;

        // We fetch a sample of the data to assess how much it has changed
        int sampleSize = (int) Math.min(20, 0.25 * numGoodDocuments);
        Map<EdgeUrl, CrawledDocument> referenceUrls = oldCrawlData.sample(sampleSize);

        int differences = 0;

        long crawlDelay = robotsRules.getCrawlDelay();
        for (var url : referenceUrls.keySet()) {

            var docMaybe = fetchDocument(url, crawlDelay);
            if (docMaybe.isEmpty()) {
                differences++;
                continue;
            }

            var newDoc = docMaybe.get();
            var referenceDoc = referenceUrls.get(url);

            // This looks like a bug but it is not, we want to compare references
            // to detect if the page has bounced off etag or last-modified headers
            // to avoid having to do a full content comparison
            if (newDoc == referenceDoc)
                continue;

            if (newDoc.httpStatus != referenceDoc.httpStatus) {
                differences++;
                continue;
            }

            if (newDoc.documentBody == null) {
                differences++;
                continue;
            }

            long referenceLsh = hashDoc(referenceDoc);
            long newLsh = hashDoc(newDoc);

            if (EasyLSH.hammingDistance(referenceLsh, newLsh) > 5) {
                differences++;
            }
        }
        if (differences > sampleSize/4) {
            return CrawlDataComparison.CHANGES_FOUND;
        }
        else {
            return CrawlDataComparison.NO_CHANGES;
        }
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

            var maybeSample = fetchUrl(url).filter(sample -> sample.httpStatus == 200);
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

    private Optional<CrawledDocument> fetchDocument(EdgeUrl top, long crawlDelay) {
        logger.debug("Fetching {}", top);

        long startTime = System.currentTimeMillis();

        var doc = fetchUrl(top);
        if (doc.isPresent()) {
            var d = doc.get();
            crawledDomainWriter.accept(d);
            oldCrawlData.dispose(top);

            if (d.url != null) {
                // We may have redirected to a different path
                EdgeUrl.parse(d.url).ifPresent(url -> {
                    crawlFrontier.addVisited(url);
                    oldCrawlData.dispose(url);
                });
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

    private Optional<CrawledDocument> fetchUrl(EdgeUrl top) {
        try {
            var doc = fetchContent(top);

            if (doc.documentBody != null) {
                doc.documentBodyHash = createHash(doc.documentBody.decode());

                Optional<Document> parsedDoc = parseDoc(doc);
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

    @SneakyThrows
    private CrawledDocument fetchContent(EdgeUrl top) {
        for (int i = 0; i < 2; i++) {
            try {
                var doc = fetcher.fetchContent(top, oldCrawlData.getEtag(top), oldCrawlData.getLastModified(top));

                doc.recrawlState = "NEW";

                if (doc.httpStatus == 304) {
                    var referenceData = oldCrawlData.getDoc(top);
                    if (referenceData != null) {
                        referenceData.recrawlState = "304/UNCHANGED";
                        return referenceData;
                    }
                }


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

    private Optional<Document> parseDoc(CrawledDocument doc) {
        if (doc.documentBody == null)
            return Optional.empty();
        return Optional.of(Jsoup.parse(doc.documentBody.decode()));
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

package nu.marginalia.crawl.retreival;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.SneakyThrows;
import nu.marginalia.crawl.retreival.fetcher.FetchResult;
import nu.marginalia.crawl.retreival.fetcher.FetchResultState;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.crawling.model.*;
import nu.marginalia.ip_blocklist.UrlBlocklist;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
    private final DomainCrawlFrontier crawlFrontier;


    int errorCount = 0;

    public CrawlerRetreiver(HttpFetcher fetcher, CrawlingSpecification specs, Consumer<SerializableCrawlData> writer) {
        this.fetcher = fetcher;

        id = specs.id;
        domain = specs.domain;

        crawledDomainWriter = writer;

        this.crawlFrontier = new DomainCrawlFrontier(new EdgeDomain(domain), specs.urls, specs.crawlDepth);

        var fst = crawlFrontier.peek();
        if (fst != null) {

            // Ensure the index page is always crawled
            var root = fst.withPathAndParam("/", null);
            if (crawlFrontier.addKnown(root))
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
        long crawlDelay = robotsRules.getCrawlDelay();

        CrawledDomain ret = new CrawledDomain(id, domain, null, CrawlerDomainStatus.OK.name(), null, ip, new ArrayList<>(), null);

        int fetchedCount = 0;

        configureLinkFilter();

        while (!crawlFrontier.isEmpty()
            && !crawlFrontier.isCrawlDepthReached()
            && errorCount < MAX_ERRORS)
        {
            var top = crawlFrontier.takeNextUrl();

            if (!robotsRules.isAllowed(top.toString())) {
                crawledDomainWriter.accept(createRobotsError(top));
                continue;
            }

            if (!crawlFrontier.filterLink(top))
                continue;
            if (urlBlocklist.isUrlBlocked(top))
                continue;
            if (!isAllowedProtocol(top.proto))
                continue;
            if (top.toString().length() > 255)
                continue;
            if (!crawlFrontier.addVisited(top))
                continue;

            if (fetchDocument(top, crawlDelay)) {
                fetchedCount++;
            }
        }

        ret.cookies = fetcher.getCookies();

        crawledDomainWriter.accept(ret);

        return fetchedCount;
    }

    private void configureLinkFilter() {
        try {
            logger.info("Configuring link filter");

            fetchUrl(crawlFrontier.peek())
                    .map(linkFilterSelector::selectFilter)
                    .ifPresent(crawlFrontier::setLinkFilter);
        }
        catch (Exception ex) {
            logger.error("Error configuring link filter", ex);
        }
    }

    private boolean fetchDocument(EdgeUrl top, long crawlDelay) {
        logger.debug("Fetching {}", top);

        long startTime = System.currentTimeMillis();

        var doc = fetchUrl(top);
        if (doc.isPresent()) {
            var d = doc.get();
            crawledDomainWriter.accept(d);

            if (d.url != null) {
                EdgeUrl.parse(d.url).ifPresent(crawlFrontier::addVisited);
            }

            if ("ERROR".equals(d.crawlerStatus) && d.httpStatus != 404) {
                errorCount++;
            }

        }

        long crawledTime = System.currentTimeMillis() - startTime;
        delay(crawlDelay, crawledTime);

        return doc.isPresent();
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
                return fetcher.fetchContent(top);
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

            Thread.sleep(min(sleepTime-spentTime, 5000));
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

            Thread.sleep(sleepTime-spentTime);
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
    private CrawledDomain createErrorPostFromStatus(FetchResult ret) {
        String ip = findIp(domain);

        if (ret.state == FetchResultState.ERROR) {
            return CrawledDomain.builder()
                    .crawlerStatus(CrawlerDomainStatus.ERROR.name())
                    .id(id).domain(domain)
                    .ip(ip)
                    .build();
        }
        if (ret.state == FetchResultState.REDIRECT) {
            return CrawledDomain.builder()
                    .crawlerStatus(CrawlerDomainStatus.REDIRECT.name())
                    .id(id)
                    .domain(domain)
                    .redirectDomain(ret.domain.toString())
                    .ip(ip)
                    .build();
        }
        throw new AssertionError("Unexpected case");
    }


}

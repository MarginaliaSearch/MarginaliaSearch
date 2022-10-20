package nu.marginalia.wmsa.edge.crawling.retreival;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.converting.processor.logic.LinkParser;
import nu.marginalia.wmsa.edge.crawling.CrawledDomainWriter;
import nu.marginalia.wmsa.edge.crawling.blocklist.GeoIpBlocklist;
import nu.marginalia.wmsa.edge.crawling.blocklist.IpBlockList;
import nu.marginalia.wmsa.edge.crawling.blocklist.UrlBlocklist;
import nu.marginalia.wmsa.edge.crawling.model.*;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class CrawlerRetreiver {
    private static final long DEFAULT_CRAWL_DELAY_MIN_MS = Long.getLong("defaultCrawlDelay", 500);
    private static final long DEFAULT_CRAWL_DELAY_MAX_MS = Long.getLong("defaultCrawlDelaySlow", 2500);

    private static final int MAX_ERRORS = 10;

    private final LinkedList<EdgeUrl> queue = new LinkedList<>();
    private final HttpFetcher fetcher;

    private final HashSet<String> visited;
    private final HashSet<String> known;
    private boolean slowDown = false;

    private final int depth;
    private final String id;
    private final String domain;
    private final CrawledDomainWriter crawledDomainWriter;

    private static final LinkParser linkParser = new LinkParser();
    private static final Logger logger = LoggerFactory.getLogger(CrawlerRetreiver.class);

    private static final HashFunction hashMethod = Hashing.murmur3_128(0);
    private static final IpBlockList ipBlocklist;
    private static final UrlBlocklist urlBlocklist = new UrlBlocklist();

    int errorCount = 0;

    static {
        try {
            ipBlocklist = new IpBlockList(new GeoIpBlocklist());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CrawlerRetreiver(HttpFetcher fetcher, CrawlingSpecification specs, CrawledDomainWriter writer) {
        this.fetcher = fetcher;
        visited = new HashSet<>((int)(specs.urls.size() * 1.5));
        known = new HashSet<>(specs.urls.size() * 10);

        depth = specs.crawlDepth;
        id = specs.id;
        domain = specs.domain;

        crawledDomainWriter = writer;

        for (String urlStr : specs.urls) {
            EdgeUrl.parse(urlStr).ifPresent(this::addToQueue);
        }

        if (queue.peek() != null) {
            var fst = queue.peek();
            var root = fst.withPathAndParam("/", null);
            if (known.add(root.toString()))
                queue.addFirst(root);
        }
    }

    public int fetch() throws IOException {
        Optional<CrawledDomain> probeResult = probeDomainForProblems(domain);

        if (probeResult.isPresent()) {
            crawledDomainWriter.accept(probeResult.get());
            return 1;
        }
        else {
            return crawlDomain();
        }
    }

    private Optional<CrawledDomain> probeDomainForProblems(String domain) {
        EdgeUrl fst = queue.peek();


        if (fst == null) {
            logger.warn("No URLs for domain {}", domain);

            return Optional.of(CrawledDomain.builder()
                    .crawlerStatus(CrawlerDomainStatus.ERROR.name())
                    .crawlerStatusDesc("No known URLs")
                    .id(id)
                    .domain(domain)
                    .build());
        }

        if (!ipBlocklist.isAllowed(fst.domain)) {
            return Optional.of(CrawledDomain.builder()
                    .crawlerStatus(CrawlerDomainStatus.BLOCKED.name())
                    .id(id)
                    .domain(domain)
                    .ip(findIp(domain))
                    .build());
        }

        var fetchResult = fetcher.probeDomain(fst.withPathAndParam("/", null));
        if (!fetchResult.ok()) {
            logger.debug("Bad status on {}", domain);
            return Optional.of(createErrorPostFromStatus(fetchResult));
        }
        return Optional.empty();
    }

    private int crawlDomain() throws IOException {
        String ip = findIp(domain);

        assert !queue.isEmpty();

        var robotsRules = fetcher.fetchRobotRules(queue.peek().domain);
        long crawlDelay = robotsRules.getCrawlDelay();

        CrawledDomain ret = new CrawledDomain(id, domain, null, CrawlerDomainStatus.OK.name(), null, ip, new ArrayList<>(), null);

        int fetchedCount = 0;

        while (!queue.isEmpty() && visited.size() < depth && errorCount < MAX_ERRORS ) {
            var top = queue.removeFirst();

            if (!robotsRules.isAllowed(top.toString())) {
                crawledDomainWriter.accept(createRobotsError(top));
                continue;
            }

            if (urlBlocklist.isUrlBlocked(top))
                continue;
            if (!isAllowedProtocol(top.proto))
                continue;
            if (top.toString().length() > 255)
                continue;
            if (!visited.add(top.toString()))
                continue;

            if (fetchDocument(top, crawlDelay)) {
                fetchedCount++;
            }
        }

        ret.cookies = fetcher.getCookies();

        crawledDomainWriter.accept(ret);

        return fetchedCount;
    }

    private boolean fetchDocument(EdgeUrl top, long crawlDelay) throws IOException {
        logger.debug("Fetching {}", top);
        long startTime = System.currentTimeMillis();

        var doc = fetchUrl(top);
        if (doc.isPresent()) {
            var d = doc.get();
            crawledDomainWriter.accept(d);

            if (d.url != null) {
                EdgeUrl.parse(d.url).map(EdgeUrl::toString).ifPresent(visited::add);
            }

            if ("ERROR".equals(d.crawlerStatus)) {
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

                doc.documentBodyHash = createHash(doc.documentBody);

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
        return Optional.of(Jsoup.parse(doc.documentBody));
    }

    public boolean isSameDomain(EdgeUrl url) {
        return domain.equals(url.domain.toString().toLowerCase());
    }

    private void findLinks(EdgeUrl baseUrl, Document parsed) {
        baseUrl = linkParser.getBaseLink(parsed, baseUrl);

        for (var link : parsed.getElementsByTag("a")) {
            linkParser.parseLink(baseUrl, link).ifPresent(this::addToQueue);
        }
        for (var link : parsed.getElementsByTag("frame")) {
            linkParser.parseFrame(baseUrl, link).ifPresent(this::addToQueue);
        }
        for (var link : parsed.getElementsByTag("iframe")) {
            linkParser.parseFrame(baseUrl, link).ifPresent(this::addToQueue);
        }
    }

    private void addToQueue(EdgeUrl url) {
        if (!isSameDomain(url))
            return;
        if (urlBlocklist.isUrlBlocked(url))
            return;
        if (urlBlocklist.isMailingListLink(url))
            return;
        // reduce memory usage by not growing queue huge when crawling large sites
        if (queue.size() + visited.size() >= depth + 100)
            return;

        if (known.add(url.toString())) {
            queue.addLast(url);
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
    private CrawledDomain createErrorPostFromStatus(HttpFetcher.FetchResult ret) {
        String ip = findIp(domain);

        if (ret.state == HttpFetcher.FetchResultState.ERROR) {
            return CrawledDomain.builder()
                    .crawlerStatus(CrawlerDomainStatus.ERROR.name())
                    .id(id).domain(domain)
                    .ip(ip)
                    .build();
        }
        if (ret.state == HttpFetcher.FetchResultState.REDIRECT) {
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

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
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;

public class CrawlerRetreiver {
    private static final long DEFAULT_CRAWL_DELAY_MS = Long.getLong("defaultCrawlDelay", 1000);
    private final LinkedList<EdgeUrl> queue = new LinkedList<>();
    private final HttpFetcher fetcher;
    private final HashSet<EdgeUrl> visited;
    private final HashSet<EdgeUrl> known;

    private final int depth;
    private final String id;
    private final String domain;
    private final CrawledDomainWriter crawledDomainWriter;

    private static final LinkParser linkParser = new LinkParser();
    private static final Logger logger = LoggerFactory.getLogger(CrawlerRetreiver.class);

    private static final HashFunction hashMethod = Hashing.murmur3_128(0);
    private static final IpBlockList ipBlocklist;
    private static final UrlBlocklist urlBlocklist = new UrlBlocklist();

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
            EdgeUrl.parse(urlStr)
                    .filter(known::add)
                    .ifPresent(queue::addLast);
        }

        if (queue.peek() != null) {
            var fst = queue.peek();
            var root = fst.domain.toRootUrl();
            if (known.add(root))
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

        var fetchResult = fetcher.probeDomain(fst.domain.toRootUrl());
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

        CrawledDomain ret = new CrawledDomain(id, domain, null, CrawlerDomainStatus.OK.name(), null, ip, Collections.emptyList(), null);

        int fetchedCount = 0;

        while (!queue.isEmpty() && visited.size() < depth) {
            var top = queue.removeFirst();

            if (!robotsRules.isAllowed(top.toString())) {
                ret.doc.add(createRobotsError(top));
                continue;
            }

            if (urlBlocklist.isUrlBlocked(top))
                continue;
            if (!isAllowedProtocol(top.proto))
                continue;
            if (top.toString().length() > 255)
                continue;
            if (!visited.add(top))
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
                try {
                    visited.add(new EdgeUrl(d.url));
                } catch (URISyntaxException ex) {}
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

            var doc = fetcher.fetchContent(top);

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
            linkParser.parseLink(baseUrl, link)
                    .filter(this::isSameDomain)
                    .filter(u -> !urlBlocklist.isUrlBlocked(u))
                    .filter(u -> !urlBlocklist.isMailingListLink(u))
                    .filter(known::add)
                    .ifPresent(queue::addLast);
        }
        for (var link : parsed.getElementsByTag("frame")) {
            linkParser.parseFrame(baseUrl, link)
                    .filter(this::isSameDomain)
                    .filter(u -> !urlBlocklist.isUrlBlocked(u))
                    .filter(u -> !urlBlocklist.isMailingListLink(u))
                    .filter(known::add)
                    .ifPresent(queue::addLast);
        }
        for (var link : parsed.getElementsByTag("iframe")) {
            linkParser.parseFrame(baseUrl, link)
                    .filter(this::isSameDomain)
                    .filter(u -> !urlBlocklist.isUrlBlocked(u))
                    .filter(u -> !urlBlocklist.isMailingListLink(u))
                    .filter(known::add)
                    .ifPresent(queue::addLast);
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

            Thread.sleep(Math.min(sleepTime-spentTime, 5000));
        }
        else {
            if (spentTime > DEFAULT_CRAWL_DELAY_MS)
                return;

            Thread.sleep(DEFAULT_CRAWL_DELAY_MS - spentTime);
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

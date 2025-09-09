package nu.marginalia.crawl.fetcher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import nu.marginalia.UserAgent;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.retreival.CrawlDelayTimer;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.ContentTypeLogic;
import nu.marginalia.model.body.DocumentBodyExtractor;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.model.crawldata.CrawlerDomainStatus;
import nu.marginalia.proxy.SocksProxyConfiguration;
import nu.marginalia.proxy.SocksProxyManager;
import nu.marginalia.proxy.SocksProxyHttpClientFactory;
import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


@Singleton
public class HttpFetcherImpl implements HttpFetcher, HttpRequestRetryStrategy {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String userAgentString;
    private final String userAgentIdentifier;

    private final CookieStore cookies = new BasicCookieStore();
    private final SocksProxyManager proxyManager;

    private static final SimpleRobotRulesParser robotsParser = new SimpleRobotRulesParser();
    private static final ContentTypeLogic contentTypeLogic = new ContentTypeLogic();
    private final Marker crawlerAuditMarker = MarkerFactory.getMarker("CRAWLER");

    private final LinkParser linkParser = new LinkParser();
    @Override
    public void setAllowAllContentTypes(boolean allowAllContentTypes) {
        contentTypeLogic.setAllowAllContentTypes(allowAllContentTypes);
    }

    private final CloseableHttpClient client;
    private PoolingHttpClientConnectionManager connectionManager;

    public PoolStats getPoolStats() {
        return connectionManager.getTotalStats();
    }

    private CloseableHttpClient createClient() throws NoSuchAlgorithmException, KeyManagementException {
        final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setSocketTimeout(10, TimeUnit.SECONDS)
                .setConnectTimeout(30, TimeUnit.SECONDS)
                .setValidateAfterInactivity(TimeValue.ofSeconds(5))
                .build();

        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnPerRoute(2)
                .setMaxConnTotal(5000)
                .setDefaultConnectionConfig(connectionConfig)
                .setTlsSocketStrategy(new DefaultClientTlsStrategy(SSLContext.getDefault()));

        // Configure SOCKS proxy if enabled
        SocksProxyConfiguration.SocksProxy selectedProxy = proxyManager.selectProxy();
        SocksProxyHttpClientFactory.configureConnectionManager(connectionManagerBuilder, selectedProxy);

        connectionManager = connectionManagerBuilder.build();

        connectionManager.setDefaultSocketConfig(SocketConfig.custom()
                .setSoLinger(TimeValue.ofSeconds(-1))
                .setSoTimeout(Timeout.ofSeconds(10))
                .build()
        );

        Thread.ofPlatform().daemon(true).start(() -> {
            try {
                for (;;) {
                    TimeUnit.SECONDS.sleep(15);
                    logger.info("Connection pool stats: {}", connectionManager.getTotalStats());
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        final RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setCookieSpec(StandardCookieSpec.RELAXED)
                .setResponseTimeout(10, TimeUnit.SECONDS)
                .setConnectionRequestTimeout(5, TimeUnit.MINUTES)
                .build();

        return HttpClients.custom()
                .setDefaultCookieStore(cookies)
                .setConnectionManager(connectionManager)
                .setRetryStrategy(this)
                .setKeepAliveStrategy(new ConnectionKeepAliveStrategy() {
                    // Default keep-alive duration is 3 minutes, but this is too long for us,
                    // as we are either going to re-use it fairly quickly or close it for a long time.
                    //
                    // So we set it to 30 seconds or clamp the server-provided value to a minimum of 10 seconds.
                    private static final TimeValue defaultValue = TimeValue.ofSeconds(30);

                    @Override
                    public TimeValue getKeepAliveDuration(HttpResponse response, HttpContext context) {
                        final Iterator<HeaderElement> it = MessageSupport.iterate(response, HeaderElements.KEEP_ALIVE);

                        while (it.hasNext()) {
                            final HeaderElement he = it.next();
                            final String param = he.getName();
                            final String value = he.getValue();

                            if (value == null)
                                continue;
                            if (!"timeout".equalsIgnoreCase(param))
                                continue;

                            try {
                                long timeout = Long.parseLong(value);
                                timeout = Math.clamp(timeout, 30, defaultValue.toSeconds());
                                return TimeValue.ofSeconds(timeout);
                            } catch (final NumberFormatException ignore) {
                                break;
                            }
                        }
                        return defaultValue;
                    }
                })
                .disableRedirectHandling()
                .setDefaultRequestConfig(defaultRequestConfig)
                .build();
    }

    @Override
    public CookieStore getCookies() {
        return cookies;
    }

    @Override
    public void clearCookies() {
        cookies.clear();
    }

    @Inject
    public HttpFetcherImpl(UserAgent userAgent)
    {
        this.proxyManager = new SocksProxyManager(new SocksProxyConfiguration());
        try {
            this.client = createClient();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }
        this.userAgentString = userAgent.uaString();
        this.userAgentIdentifier = userAgent.uaIdentifier();
    }

    public HttpFetcherImpl(String userAgent) {
        this.proxyManager = new SocksProxyManager(new SocksProxyConfiguration());
        try {
            this.client = createClient();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }
        this.userAgentString = userAgent;
        this.userAgentIdentifier = userAgent;
    }

    // Not necessary in prod, but useful in test
    public void close() throws IOException {
        client.close();
    }

    /**
     * Probe the domain to see if it is reachable, attempting to identify which schema to use,
     * and if there are any redirects.  This is done by one or more HEAD requests.
     *
     * @param url The URL to probe.
     * @return The result of the probe, indicating the state and the URL.
     */
    @Override
    public DomainProbeResult probeDomain(EdgeUrl url) {
        List<EdgeUrl> urls = new ArrayList<>();
        urls.add(url);

        int redirects = 0;
        AtomicBoolean tryGet = new AtomicBoolean(false);

        while (!urls.isEmpty() && ++redirects < 5) {
            ClassicHttpRequest request;

            EdgeUrl topUrl = urls.removeFirst();
            try {
                if (tryGet.get()) {
                    request = ClassicRequestBuilder.get(topUrl.asURI())
                                .addHeader("User-Agent", userAgentString)
                                .addHeader("Accept-Encoding", "gzip")
                                .addHeader("Range", "bytes=0-255")
                                .build();
                } else {
                    request = ClassicRequestBuilder.head(topUrl.asURI())
                                .addHeader("User-Agent", userAgentString)
                                .addHeader("Accept-Encoding", "gzip")
                                .build();
                }
            } catch (URISyntaxException e) {
                return new DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "Invalid URL");
            }

            try {
                var result = SendLock.wrapSend(client, request, response -> {
                    EntityUtils.consume(response.getEntity());

                    return switch (response.getCode()) {
                        case 200 -> new DomainProbeResult.Ok(url);
                        case 405 -> {
                            if (!tryGet.get()) {
                                tryGet.set(true);
                                yield new DomainProbeResult.RedirectSameDomain_Internal(url);
                            }
                            else {
                                yield new DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "HTTP status 405, tried HEAD and GET?!");
                            }
                        }
                        case 301, 302, 307 -> {
                            var location = response.getFirstHeader("Location");

                            if (location != null) {
                                Optional<EdgeUrl> newUrl = linkParser.parseLink(topUrl, location.getValue());
                                if (newUrl.isEmpty()) {
                                    yield new DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "Invalid location header on redirect");
                                }
                                EdgeUrl newEdgeUrl = newUrl.get();
                                if (newEdgeUrl.domain.equals(topUrl.domain)) {
                                    yield new DomainProbeResult.RedirectSameDomain_Internal(newEdgeUrl);
                                }
                                else {
                                    yield new DomainProbeResult.Redirect(newEdgeUrl.domain);
                                }
                            }

                            yield new DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "No location header on redirect");

                        }
                        default ->
                                new DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "HTTP status " + response.getCode());
                    };
                });

                if (result instanceof DomainProbeResult.RedirectSameDomain_Internal(EdgeUrl redirUrl)) {
                    urls.add(redirUrl);
                }
                else {
                    return result;
                }

                // We don't have robots.txt yet, so we'll assume a request delay of 1 second
                TimeUnit.SECONDS.sleep(1);
            }
            catch (SocketTimeoutException ex) {
                return new DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "Timeout during domain probe");
            }
            catch (Exception ex) {
                return new DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "Error during domain probe");
            }

        }

        return new DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "Failed to resolve domain root");

    }

    /** Perform a HEAD request to fetch the content type of a URL.
     * If the content type is not allowed, flag the URL as a failed
     * content type probe.
     * <p></p>
     * The outcome of the probe is returned, and the result is also
     * recorded in the WARC file on failure.
     */
    public ContentTypeProbeResult probeContentType(EdgeUrl url,
                                                   DomainCookies cookies,
                                                   CrawlDelayTimer timer,
                                                   ContentTags tags) {
        if (!tags.isEmpty() || !contentTypeLogic.isUrlLikeBinary(url)) {
            return new ContentTypeProbeResult.NoOp();
        }

        try {
            ClassicHttpRequest head = ClassicRequestBuilder.head(url.asURI())
                    .addHeader("User-Agent", userAgentString)
                    .addHeader("Accept-Encoding", "gzip")
                    .build();

            cookies.paintRequest(head);

            return SendLock.wrapSend(client, head, (rsp) -> {
                cookies.updateCookieStore(rsp);
                EntityUtils.consume(rsp.getEntity());
                int statusCode = rsp.getCode();

                // Handle redirects
                if (statusCode == 301 || statusCode == 302 || statusCode == 307) {
                    var location = rsp.getFirstHeader("Location");
                    if (location != null) {
                        Optional<EdgeUrl> newUrl = linkParser.parseLink(url, location.getValue());
                        if (newUrl.isEmpty())
                            return new ContentTypeProbeResult.HttpError(statusCode, "Invalid location header on redirect");
                        return new ContentTypeProbeResult.Redirect(newUrl.get());
                    }
                }

                if (statusCode == 405) {
                    // If we get a 405, we can't probe the content type with HEAD, so we'll just say it's ok
                    return new ContentTypeProbeResult.Ok(url);
                }

                // Handle errors
                if (statusCode < 200 || statusCode > 300) {
                    return new ContentTypeProbeResult.HttpError(statusCode, "Bad status code");
                }

                // Handle missing content type
                var ctHeader = rsp.getFirstHeader("Content-Type");
                if (ctHeader == null) {
                    return new ContentTypeProbeResult.HttpError(statusCode, "Missing Content-Type header");
                }
                var contentType = ctHeader.getValue();

                // Check if the content type is allowed
                if (contentTypeLogic.isAllowableContentType(contentType)) {
                    return new ContentTypeProbeResult.Ok(url);
                } else {
                    return new ContentTypeProbeResult.BadContentType(contentType, statusCode);
                }
            });
        }
        catch (SocketTimeoutException ex) {

            return new ContentTypeProbeResult.Timeout(ex);
        }
        catch (Exception ex) {
            logger.error("Error during fetching {}[{}]", ex.getClass().getSimpleName(), ex.getMessage());
            return new ContentTypeProbeResult.Exception(ex);
        }
        finally {
            timer.waitFetchDelay();
        }
    }

    /** Fetch the content of a URL, and record it in a WARC file,
     * returning a result object that can be used to determine
     * the outcome of the fetch.
     */
    @Override
    public HttpFetchResult fetchContent(EdgeUrl url,
                                           WarcRecorder warcRecorder,
                                           DomainCookies cookies,
                                           CrawlDelayTimer timer,
                                           ContentTags contentTags,
                                           ProbeType probeType)
    {
        try {
            if (probeType == HttpFetcher.ProbeType.FULL) {
                try {
                    var probeResult = probeContentType(url, cookies, timer, contentTags);

                    switch (probeResult) {
                        case HttpFetcher.ContentTypeProbeResult.NoOp():
                            break; //
                        case HttpFetcher.ContentTypeProbeResult.Ok(EdgeUrl resolvedUrl):
                            logger.info(crawlerAuditMarker, "Probe result OK for {}", url);
                            url = resolvedUrl; // If we were redirected while probing, use the final URL for fetching
                            break;
                        case ContentTypeProbeResult.BadContentType badContentType:
                            warcRecorder.flagAsFailedContentTypeProbe(url, badContentType.contentType(), badContentType.statusCode());
                            logger.info(crawlerAuditMarker, "Probe result Bad ContenType ({}) for {}", badContentType.contentType(), url);
                            return new HttpFetchResult.ResultNone();
                        case ContentTypeProbeResult.BadContentType.Timeout(Exception ex):
                            logger.info(crawlerAuditMarker, "Probe result Timeout for {}", url);
                            warcRecorder.flagAsTimeout(url);
                            return new HttpFetchResult.ResultException(ex);
                        case ContentTypeProbeResult.Exception(Exception ex):
                            logger.info(crawlerAuditMarker, "Probe result Exception({}) for {}", ex.getClass().getSimpleName(), url);
                            warcRecorder.flagAsError(url, ex);
                            return new HttpFetchResult.ResultException(ex);
                        case ContentTypeProbeResult.HttpError httpError:
                            logger.info(crawlerAuditMarker, "Probe result HTTP Error ({}) for {}", httpError.statusCode(), url);
                            return new HttpFetchResult.ResultException(new HttpException("HTTP status code " + httpError.statusCode() + ": " + httpError.message()));
                        case ContentTypeProbeResult.Redirect redirect:
                            logger.info(crawlerAuditMarker, "Probe result redirect for {} -> {}", url, redirect.location());
                            return new HttpFetchResult.ResultRedirect(redirect.location());
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to fetch {}", url, ex);
                    return new HttpFetchResult.ResultException(ex);
                }

            }

            HttpGet request = new HttpGet(url.asURI());
            request.addHeader("User-Agent", userAgentString);
            request.addHeader("Accept-Encoding", "gzip");
            request.addHeader("Accept-Language", "en,*;q=0.5");
            request.addHeader("Accept", "text/html, application/xhtml+xml, text/*;q=0.8");

            contentTags.paint(request);

            try (var sl = new SendLock()) {
                Instant start = Instant.now();
                HttpFetchResult result = warcRecorder.fetch(client, cookies, request);

                Duration fetchDuration = Duration.between(start, Instant.now());

                if (result instanceof HttpFetchResult.ResultOk ok) {
                    if (ok.statusCode() == 304) {
                        result = new HttpFetchResult.Result304Raw();
                    }
                }

                switch (result) {
                    case HttpFetchResult.ResultOk ok -> logger.info(crawlerAuditMarker, "Fetch result OK {} for {} ({} ms)", ok.statusCode(), url, fetchDuration.toMillis());
                    case HttpFetchResult.ResultRedirect redirect -> logger.info(crawlerAuditMarker, "Fetch result redirect: {}  for {}", redirect.url(), url);
                    case HttpFetchResult.ResultNone none -> logger.info(crawlerAuditMarker, "Fetch result none for {}", url);
                    case HttpFetchResult.ResultException ex -> logger.error(crawlerAuditMarker, "Fetch result exception for {}", url, ex.ex());
                    case HttpFetchResult.Result304Raw raw -> logger.info(crawlerAuditMarker, "Fetch result: 304 Raw for {}", url);
                    case HttpFetchResult.Result304ReplacedWithReference ref -> logger.info(crawlerAuditMarker, "Fetch result: 304 With reference for {}", url);
                }

                return result;
            }
        }
        catch (Exception ex) {
            logger.error(crawlerAuditMarker, "Fetch result exception for {}", url, ex);

            return new HttpFetchResult.ResultException(ex);
        }

    }

    @Override
    public SitemapRetriever createSitemapRetriever() {
        return new SitemapRetriever();
    }

    /** Recursively fetch sitemaps */
    @Override
    public List<EdgeUrl> fetchSitemapUrls(String root, CrawlDelayTimer delayTimer) {
        try {
            List<EdgeUrl> ret = new ArrayList<>();

            Set<String> seenUrls = new HashSet<>();
            Set<String> seenSitemaps = new HashSet<>();

            Deque<EdgeUrl> sitemapQueue = new LinkedList<>();

            EdgeUrl rootSitemapUrl = new EdgeUrl(root);

            sitemapQueue.add(rootSitemapUrl);

            int fetchedSitemaps = 0;

            while (!sitemapQueue.isEmpty() && ret.size() < 20_000 && ++fetchedSitemaps < 10) {
                var head = sitemapQueue.removeFirst();

                switch (fetchSingleSitemap(head)) {
                    case SitemapResult.SitemapUrls(List<String> urls) -> {

                        for (var url : urls) {
                            if (seenUrls.add(url)) {
                                EdgeUrl.parse(url)
                                        .filter(u -> u.domain.equals(rootSitemapUrl.domain))
                                        .ifPresent(ret::add);
                            }
                        }

                    }
                    case SitemapResult.SitemapReferences(List<String> refs) -> {
                        for (var ref : refs) {
                            if (seenSitemaps.add(ref)) {
                                EdgeUrl.parse(ref)
                                        .filter(url -> url.domain.equals(rootSitemapUrl.domain))
                                        .ifPresent(sitemapQueue::addFirst);
                            }
                        }
                    }
                    case SitemapResult.SitemapError() -> {}
                }

                delayTimer.waitFetchDelay();
            }

            return ret;
        }
        catch (Exception ex) {
            logger.error("Error while fetching sitemaps via {}: {} ({})", root, ex.getClass().getSimpleName(), ex.getMessage());
            return List.of();
        }
    }


    private SitemapResult fetchSingleSitemap(EdgeUrl sitemapUrl) throws URISyntaxException {
        HttpGet getRequest = new HttpGet(sitemapUrl.asURI());

        getRequest.addHeader("User-Agent", userAgentString);
        getRequest.addHeader("Accept-Encoding", "gzip");
        getRequest.addHeader("Accept", "text/*, */*;q=0.9");
        getRequest.addHeader("User-Agent", userAgentString);

        try (var sl = new SendLock()) {
            return client.execute(getRequest, response -> {
                try {
                    if (response.getCode() != 200) {
                        return new SitemapResult.SitemapError();
                    }

                    Document parsedSitemap = Jsoup.parse(
                            EntityUtils.toString(response.getEntity()),
                            sitemapUrl.toString(),
                            Parser.xmlParser()
                    );

                    if (parsedSitemap.childrenSize() == 0) {
                        return new SitemapResult.SitemapError();
                    }

                    String rootTagName = parsedSitemap.child(0).tagName();

                    return switch (rootTagName.toLowerCase()) {
                        case "sitemapindex" -> {
                            List<String> references = new ArrayList<>();
                            for (var locTag : parsedSitemap.getElementsByTag("loc")) {
                                references.add(locTag.text().trim());
                            }
                            yield new SitemapResult.SitemapReferences(Collections.unmodifiableList(references));
                        }
                        case "urlset" -> {
                            List<String> urls = new ArrayList<>();
                            for (var locTag : parsedSitemap.select("url > loc")) {
                                urls.add(locTag.text().trim());
                            }
                            yield new SitemapResult.SitemapUrls(Collections.unmodifiableList(urls));
                        }
                        case "rss", "atom" -> {
                            List<String> urls = new ArrayList<>();
                            for (var locTag : parsedSitemap.select("link, url")) {
                                urls.add(locTag.text().trim());
                            }
                            yield new SitemapResult.SitemapUrls(Collections.unmodifiableList(urls));
                        }
                        default -> new SitemapResult.SitemapError();
                    };
                }
                finally {
                    EntityUtils.consume(response.getEntity());
                }
            });
        }
        catch (Exception ex) {
            logger.warn("Error while fetching sitemap {}: {} ({})", sitemapUrl, ex.getClass().getSimpleName(), ex.getMessage());
            return new SitemapResult.SitemapError();
        }
    }

    private sealed interface SitemapResult {
        record SitemapUrls(List<String> urls) implements SitemapResult {}
        record SitemapReferences(List<String> sitemapRefs) implements SitemapResult {}
        record SitemapError() implements SitemapResult {}
    }

    @Override
    public SimpleRobotRules fetchRobotRules(EdgeDomain domain, WarcRecorder recorder) {
        var ret = fetchAndParseRobotsTxt(new EdgeUrl("https", domain, null, "/robots.txt", null), recorder);
        if (ret.isPresent())
            return ret.get();

        ret = fetchAndParseRobotsTxt(new EdgeUrl("http", domain, null, "/robots.txt", null), recorder);
        if (ret.isPresent())
            return ret.get();

        return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
    }

    private Optional<SimpleRobotRules> fetchAndParseRobotsTxt(EdgeUrl url, WarcRecorder recorder) {
        try (var sl = new SendLock()) {

            HttpGet request = new HttpGet(url.asURI());
            request.addHeader("User-Agent", userAgentString);
            request.addHeader("Accept-Encoding", "gzip");
            request.addHeader("Accept", "text/*, */*;q=0.9");

            HttpFetchResult result = recorder.fetch(client, new DomainCookies(), request);

            return DocumentBodyExtractor.asBytes(result).mapOpt((contentType, body) ->
                robotsParser.parseContent(url.toString(),
                        body,
                        contentType.toString(),
                        userAgentIdentifier)
            );
        }
        catch (Exception ex) {
            return Optional.empty();
        }
    }

    @Override
    public boolean retryRequest(HttpRequest request, IOException exception, int executionCount, HttpContext context) {
        return switch (exception) {
            case SocketTimeoutException ste -> false;
            case SSLException ssle -> false;
            case UnknownHostException uhe -> false;
            default -> executionCount <= 3;
        };
    }

    @Override
    public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
        return switch (response.getCode()) {
            case 500, 503 -> executionCount <= 2;
            case 429 -> executionCount <= 3;
            default -> false;
        };
    }

    @Override
    public TimeValue getRetryInterval(HttpRequest request, IOException exception, int executionCount, HttpContext context) {
        return TimeValue.ofSeconds(1);
    }

    @Override
    public TimeValue getRetryInterval(HttpResponse response, int executionCount, HttpContext context) {

        int statusCode = response.getCode();

        // Give 503 a bit more time
        if (statusCode == 503) return TimeValue.ofSeconds(5);

        if (statusCode == 429) {
            // get the Retry-After header
            String retryAfter = response.getFirstHeader("Retry-After").getValue();
            if (retryAfter == null) {
                return TimeValue.ofSeconds(2);
            }

            try {
                int retryAfterTime = Integer.parseInt(retryAfter);
                retryAfterTime = Math.clamp(retryAfterTime, 1, 5);

                return TimeValue.ofSeconds(retryAfterTime);
            } catch (NumberFormatException e) {
                logger.warn("Invalid Retry-After header: {}", retryAfter);
            }
        }

        return TimeValue.ofSeconds(2);
    }

    public static class RateLimitException extends Exception {
        private final String retryAfter;

        public RateLimitException(String retryAfterHeader) {
            this.retryAfter = retryAfterHeader;
        }

        @Override
        public StackTraceElement[] getStackTrace() { return new StackTraceElement[0]; }

        public Duration retryAfter() {
            try {
                return Duration.ofSeconds(Integer.parseInt(retryAfter));
            }
            catch (NumberFormatException ex) {
                return Duration.ofSeconds(1);
            }
        }
    }

}

class SendLock implements AutoCloseable {

    private static final Semaphore maxConcurrentRequests = new Semaphore(Integer.getInteger("crawler.maxConcurrentRequests", 512));
    boolean closed = false;

    public SendLock() {
        maxConcurrentRequests.acquireUninterruptibly();
    }

    public static <T> T wrapSend(HttpClient client, final ClassicHttpRequest request,
                                               final HttpClientResponseHandler<? extends T> responseHandler) throws IOException {
        try (var lock = new SendLock()) {
            return client.execute(request, responseHandler);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            maxConcurrentRequests.release();
            closed = true;
        }
    }
}


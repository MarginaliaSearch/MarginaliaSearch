package nu.marginalia.crawl.fetcher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import nu.marginalia.UserAgent;
import nu.marginalia.crawl.fetcher.socket.NoSecuritySSL;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.retreival.CrawlDelayTimer;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.ContentTypeLogic;
import nu.marginalia.model.body.DocumentBodyExtractor;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.model.crawldata.CrawlerDomainStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;


@Singleton
public class HttpFetcherImpl implements HttpFetcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String userAgentString;
    private final String userAgentIdentifier;
    private final Cookies cookies = new Cookies();

    private static final SimpleRobotRulesParser robotsParser = new SimpleRobotRulesParser();
    private static final ContentTypeLogic contentTypeLogic = new ContentTypeLogic();

    private final Duration requestTimeout = Duration.ofSeconds(10);

    @Override
    public void setAllowAllContentTypes(boolean allowAllContentTypes) {
        contentTypeLogic.setAllowAllContentTypes(allowAllContentTypes);
    }

    private final HttpClient client;

    private HttpClient createClient() {
        return HttpClient.newBuilder()
                .sslContext(NoSecuritySSL.buildSslContext())
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(8))
                .executor(Executors.newCachedThreadPool())
                .build();
    }

    @Override
    public Cookies getCookies() {
        return cookies;
    }

    @Override
    public void clearCookies() {
        cookies.clear();
    }

    @Inject
    public HttpFetcherImpl(UserAgent userAgent)
    {
        this.client = createClient();
        this.userAgentString = userAgent.uaString();
        this.userAgentIdentifier = userAgent.uaIdentifier();
    }

    public HttpFetcherImpl(String userAgent) {
        this.client = createClient();
        this.userAgentString = userAgent;
        this.userAgentIdentifier = userAgent;
    }

    // Not necessary in prod, but useful in test
    public void close() {
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
        HttpRequest head;
        try {
            head = HttpRequest.newBuilder()
                    .HEAD()
                    .uri(url.asURI())
                    .header("User-agent", userAgentString)
                    .timeout(requestTimeout)
                    .build();
        } catch (URISyntaxException e) {
            return new DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "Invalid URL");
        }

        try {
            var rsp = client.send(head, HttpResponse.BodyHandlers.discarding());
            EdgeUrl rspUri = new EdgeUrl(rsp.uri());

            if (!Objects.equals(rspUri.domain, url.domain)) {
                return new DomainProbeResult.Redirect(rspUri.domain);
            }
            return new DomainProbeResult.Ok(rspUri);
        }
        catch (Exception ex) {
            return new DomainProbeResult.Error(CrawlerDomainStatus.ERROR, ex.getMessage());
        }
    }

    /** Perform a HEAD request to fetch the content type of a URL.
     * If the content type is not allowed, flag the URL as a failed
     * content type probe.
     * <p></p>
     * The outcome of the probe is returned, and the result is also
     * recorded in the WARC file on failure.
     */
    public ContentTypeProbeResult probeContentType(EdgeUrl url,
                                                   WarcRecorder warcRecorder,
                                                   ContentTags tags) throws RateLimitException {
        if (tags.isEmpty() && contentTypeLogic.isUrlLikeBinary(url)) {

            try {
                var headBuilder = HttpRequest.newBuilder()
                    .HEAD()
                    .uri(url.asURI())
                    .header("User-agent", userAgentString)
                    .header("Accept-Encoding", "gzip")
                    .timeout(requestTimeout)
                    ;

                var rsp = client.send(headBuilder.build(), HttpResponse.BodyHandlers.discarding());
                var headers = rsp.headers();

                var contentTypeHeader = headers.firstValue("Content-Type").orElse(null);

                if (contentTypeHeader != null && !contentTypeLogic.isAllowableContentType(contentTypeHeader)) {
                    warcRecorder.flagAsFailedContentTypeProbe(url, contentTypeHeader, rsp.statusCode());

                    return new ContentTypeProbeResult.BadContentType(contentTypeHeader, rsp.statusCode());
                }

                // Update the URL to the final URL of the HEAD request, otherwise we might end up doing

                // HEAD 301 url1 -> url2
                // HEAD 200 url2
                // GET 301 url1 -> url2
                // GET 200 url2

                // which is not what we want. Overall we want to do as few requests as possible to not raise
                // too many eyebrows when looking at the logs on the target server.  Overall it's probably desirable
                // that it looks like the traffic makes sense, as opposed to looking like a broken bot.

                var redirectUrl = new EdgeUrl(rsp.uri());
                EdgeUrl ret;

                if (Objects.equals(redirectUrl.domain, url.domain)) ret = redirectUrl;
                else ret = url;

                // Intercept rate limiting
                if (rsp.statusCode() == 429) {
                    throw new HttpFetcherImpl.RateLimitException(headers.firstValue("Retry-After").orElse("1"));
                }

                return new ContentTypeProbeResult.Ok(ret);
            }
            catch (HttpTimeoutException ex) {
                warcRecorder.flagAsTimeout(url);
                return new ContentTypeProbeResult.Timeout(ex);
            }
            catch (RateLimitException ex) {
                throw ex;
            }
            catch (Exception ex) {
                logger.error("Error during fetching {}[{}]", ex.getClass().getSimpleName(), ex.getMessage());

                warcRecorder.flagAsError(url, ex);

                return new ContentTypeProbeResult.Exception(ex);
            }
        }
        return new ContentTypeProbeResult.Ok(url);
    }

    /** Fetch the content of a URL, and record it in a WARC file,
     * returning a result object that can be used to determine
     * the outcome of the fetch.
     */
    @Override
    public HttpFetchResult fetchContent(EdgeUrl url,
                                           WarcRecorder warcRecorder,
                                           ContentTags contentTags,
                                           ProbeType probeType)
        throws Exception
    {
        var getBuilder = HttpRequest.newBuilder()
                .GET()
                .uri(url.asURI())
                .header("User-agent", userAgentString)
                .header("Accept-Encoding", "gzip")
                .header("Accept-Language", "en,*;q=0.5")
                .header("Accept", "text/html, application/xhtml+xml, text/*;q=0.8")
                .timeout(requestTimeout)
                ;

        contentTags.paint(getBuilder);

        HttpFetchResult result = warcRecorder.fetch(client, getBuilder.build());

        if (result instanceof HttpFetchResult.ResultOk ok) {
            if (ok.statusCode() == 429) {
                throw new RateLimitException(Objects.requireNonNullElse(ok.header("Retry-After"), "1"));
            }
            if (ok.statusCode() == 304) {
                return new HttpFetchResult.Result304Raw();
            }
            if (ok.statusCode() == 200) {
                return ok;
            }
        }

        return result;
    }

    @Override
    public SitemapRetriever createSitemapRetriever() {
        return new SitemapRetriever();
    }

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

                switch (fetchSitemap(head)) {
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


    private SitemapResult fetchSitemap(EdgeUrl sitemapUrl) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest getRequest = HttpRequest.newBuilder()
                .GET()
                .uri(sitemapUrl.asURI())
                .header("Accept-Encoding", "gzip")
                .header("Accept", "text/*, */*;q=0.9")
                .header("User-agent", userAgentString)
                .timeout(requestTimeout)
                .build();

        var response = client.send(getRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            return new SitemapResult.SitemapError();
        }

        try (InputStream inputStream = response.body()) {

            InputStream parserStream;
            if (sitemapUrl.path.endsWith(".gz")) {
                parserStream = new GZIPInputStream(inputStream);
            }
            else {
                parserStream = inputStream;
            }

            Document parsedSitemap = Jsoup.parse(parserStream, "UTF-8", sitemapUrl.toString(), Parser.xmlParser());
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
        try {
            var getRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(url.asURI())
                    .header("Accept-Encoding", "gzip")
                    .header("Accept", "text/*, */*;q=0.9")
                    .header("User-agent", userAgentString)
                    .timeout(requestTimeout);

            HttpFetchResult result = recorder.fetch(client, getRequest.build());

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


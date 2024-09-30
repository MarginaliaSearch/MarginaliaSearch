package nu.marginalia.crawl.fetcher;

import com.google.inject.Inject;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.SneakyThrows;
import nu.marginalia.UserAgent;
import nu.marginalia.crawl.fetcher.socket.FastTerminatingSocketFactory;
import nu.marginalia.crawl.fetcher.socket.IpInterceptingNetworkInterceptor;
import nu.marginalia.crawl.fetcher.socket.NoSecuritySSL;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.ContentTypeLogic;
import nu.marginalia.model.body.DocumentBodyExtractor;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.model.crawldata.CrawlerDomainStatus;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.X509TrustManager;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;


public class HttpFetcherImpl implements HttpFetcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String userAgentString;
    private final String userAgentIdentifier;
    private final Cookies cookies = new Cookies();

    private static final SimpleRobotRulesParser robotsParser = new SimpleRobotRulesParser();
    private static final ContentTypeLogic contentTypeLogic = new ContentTypeLogic();

    @Override
    public void setAllowAllContentTypes(boolean allowAllContentTypes) {
        contentTypeLogic.setAllowAllContentTypes(allowAllContentTypes);
    }

    private final OkHttpClient client;

    private static final FastTerminatingSocketFactory ftSocketFactory = new FastTerminatingSocketFactory();

    @SneakyThrows
    private OkHttpClient createClient(Dispatcher dispatcher, ConnectionPool pool) {
        var builder = new OkHttpClient.Builder();
        if (dispatcher != null) {
            builder.dispatcher(dispatcher);
        }

        return builder.sslSocketFactory(NoSecuritySSL.buildSocketFactory(), (X509TrustManager) NoSecuritySSL.trustAllCerts[0])
            .socketFactory(ftSocketFactory)
            .hostnameVerifier(NoSecuritySSL.buildHostnameVerifyer())
            .addNetworkInterceptor(new IpInterceptingNetworkInterceptor())
            .connectionPool(pool)
            .cookieJar(cookies.getJar())
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    }

    @Override
    public List<String> getCookies() {
        return cookies.getCookies();
    }

    @Override
    public void clearCookies() {
        cookies.clear();
    }

    @Inject
    public HttpFetcherImpl(UserAgent userAgent,
                           Dispatcher dispatcher,
                           ConnectionPool connectionPool)
    {
        this.client = createClient(dispatcher, connectionPool);
        this.userAgentString = userAgent.uaString();
        this.userAgentIdentifier = userAgent.uaIdentifier();
    }

    public HttpFetcherImpl(String userAgent) {
        this.client = createClient(null, new ConnectionPool());
        this.userAgentString = userAgent;
        this.userAgentIdentifier = userAgent;
    }

    // Not necessary in prod, but useful in test
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
    /**
     * Probe the domain to see if it is reachable, attempting to identify which schema to use,
     * and if there are any redirects.  This is done by one or more HEAD requests.
     *
     * @param url The URL to probe.
     * @return The result of the probe, indicating the state and the URL.
     */
    @Override
    @SneakyThrows
    public DomainProbeResult probeDomain(EdgeUrl url) {
        var head = new Request.Builder().head().addHeader("User-agent", userAgentString)
                .url(url.toString())
                .build();

        var call = client.newCall(head);

        try (var rsp = call.execute()) {
            EdgeUrl requestUrl = new EdgeUrl(rsp.request().url().toString());

            if (!Objects.equals(requestUrl.domain, url.domain)) {
                return new DomainProbeResult.Redirect(requestUrl.domain);
            }
            return new DomainProbeResult.Ok(requestUrl);
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
        if (tags.isEmpty()) {
            var headBuilder = new Request.Builder().head()
                    .addHeader("User-agent", userAgentString)
                    .addHeader("Accept-Encoding", "gzip")
                    .url(url.toString());

            var head = headBuilder.build();
            var call = client.newCall(head);

            try (var rsp = call.execute()) {
                var contentTypeHeader = rsp.header("Content-type");

                if (contentTypeHeader != null && !contentTypeLogic.isAllowableContentType(contentTypeHeader)) {
                    warcRecorder.flagAsFailedContentTypeProbe(url, contentTypeHeader, rsp.code());

                    return new ContentTypeProbeResult.BadContentType(contentTypeHeader, rsp.code());
                }

                // Update the URL to the final URL of the HEAD request, otherwise we might end up doing

                // HEAD 301 url1 -> url2
                // HEAD 200 url2
                // GET 301 url1 -> url2
                // GET 200 url2

                // which is not what we want. Overall we want to do as few requests as possible to not raise
                // too many eyebrows when looking at the logs on the target server.  Overall it's probably desirable
                // that it looks like the traffic makes sense, as opposed to looking like a broken bot.

                var redirectUrl = new EdgeUrl(rsp.request().url().toString());
                EdgeUrl ret;

                if (Objects.equals(redirectUrl.domain, url.domain)) ret = redirectUrl;
                else ret = url;

                // Intercept rate limiting
                if (rsp.code() == 429) {
                    throw new HttpFetcherImpl.RateLimitException(Objects.requireNonNullElse(rsp.header("Retry-After"), "1"));
                }

                return new ContentTypeProbeResult.Ok(ret);
            }
            catch (RateLimitException ex) {
                throw ex;
            }
            catch (InterruptedIOException ex) {
                warcRecorder.flagAsTimeout(url);

                return new ContentTypeProbeResult.Timeout(ex);
            } catch (Exception ex) {
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
    @SneakyThrows
    public HttpFetchResult fetchContent(EdgeUrl url,
                                           WarcRecorder warcRecorder,
                                           ContentTags contentTags,
                                           ProbeType probeType)
    {
        var getBuilder = new Request.Builder().get();

        getBuilder.url(url.toString())
                .addHeader("Accept-Encoding", "gzip")
                .addHeader("Accept-Language", "en,*;q=0.5")
                .addHeader("Accept", "text/html, application/xhtml+xml, text/*;q=0.8")
                .addHeader("User-agent", userAgentString);

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
            var getBuilder = new Request.Builder().get();

            getBuilder.url(url.toString())
                    .addHeader("Accept-Encoding", "gzip")
                    .addHeader("Accept", "text/*, */*;q=0.9")
                    .addHeader("User-agent", userAgentString);

            HttpFetchResult result = recorder.fetch(client, getBuilder.build());

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


package nu.marginalia.livecrawler;

import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import nu.marginalia.WmsaHome;
import nu.marginalia.crawl.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.logic.DomainLocks;
import nu.marginalia.crawl.retreival.CrawlDelayTimer;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.util.SimpleBlockingThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** A simple link scraper that fetches URLs and stores them in a database,
 * with no concept of a crawl frontier, WARC output, or other advanced features
 */
public class SimpleLinkScraper implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SimpleLinkScraper.class);

    private final SimpleBlockingThreadPool pool = new SimpleBlockingThreadPool("LiveCrawler", 32, 10);
    private final LinkParser lp = new LinkParser();
    private final LiveCrawlDataSet dataSet;
    private final DbDomainQueries domainQueries;
    private final DomainBlacklist domainBlacklist;
    private final Duration connectTimeout = Duration.ofSeconds(10);
    private final Duration readTimeout = Duration.ofSeconds(10);
    private final DomainLocks domainLocks = new DomainLocks();

    public SimpleLinkScraper(LiveCrawlDataSet dataSet,
                             DbDomainQueries domainQueries,
                             DomainBlacklist domainBlacklist) {
        this.dataSet = dataSet;
        this.domainQueries = domainQueries;
        this.domainBlacklist = domainBlacklist;
    }

    public void scheduleRetrieval(EdgeDomain domain, List<String> urls) {

        var id = domainQueries.tryGetDomainId(domain);
        if (id.isEmpty() || domainBlacklist.isBlacklisted(id.getAsInt())) {
            return;
        }

        pool.submitQuietly(() -> retrieveNow(domain, id.getAsInt(), urls));
    }

    public void retrieveNow(EdgeDomain domain, int domainId, List<String> urls) throws Exception {
        try (HttpClient client = HttpClient
                .newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_2)
                .build();
             DomainLocks.DomainLock lock = domainLocks.lockDomain(domain) // throttle concurrent access per domain; do not remove
        ) {

            EdgeUrl rootUrl = domain.toRootUrlHttps();

            SimpleRobotRules rules = fetchRobotsRules(rootUrl, client);

            if (rules == null) { // I/O error fetching robots.txt
                // If we can't fetch the robots.txt,
                for (var url : urls) {
                    lp.parseLink(rootUrl, url).ifPresent(this::maybeFlagAsBad);
                }
                return;
            }

            CrawlDelayTimer timer = new CrawlDelayTimer(rules.getCrawlDelay());

            for (var url : urls) {
                Optional<EdgeUrl> optParsedUrl = lp.parseLink(rootUrl, url);
                if (optParsedUrl.isEmpty()) {
                    continue;
                }
                if (dataSet.hasUrl(optParsedUrl.get())) {
                    continue;
                }

                EdgeUrl parsedUrl = optParsedUrl.get();
                if (!rules.isAllowed(url)) {
                    maybeFlagAsBad(parsedUrl);
                    continue;
                }

                switch (fetchUrl(domainId, parsedUrl, timer, client)) {
                    case FetchResult.Success(int id, EdgeUrl docUrl, String body, String headers)
                            -> dataSet.saveDocument(id, docUrl, body, headers, "");
                    case FetchResult.Error(EdgeUrl docUrl) -> maybeFlagAsBad(docUrl);
                }
            }
        }
    }

    private void maybeFlagAsBad(EdgeUrl url) {
        // To give bad URLs a chance to be re-fetched, we only flag them as bad
        // with a 20% probability.  This will prevent the same bad URL being
        // re-fetched over and over again for several months, but still allow
        // us to *mostly* re-fetch it if it was just a transient error.

        // There's of course the chance we immediately flag it as bad on an
        // unlucky roll, but you know, that's xcom baby

        if (ThreadLocalRandom.current().nextDouble(0, 1) < 0.2) {
            dataSet.flagAsBad(url);
        }
    }

    @Nullable
    private SimpleRobotRules fetchRobotsRules(EdgeUrl rootUrl, HttpClient client) throws IOException, InterruptedException, URISyntaxException {
        var robotsRequest = HttpRequest.newBuilder(rootUrl.withPathAndParam("/robots.txt", null).asURI())
                .GET()
                .header("User-Agent", WmsaHome.getUserAgent().uaString())
                .timeout(readTimeout);

        // Fetch the robots.txt

        try {
            SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
            HttpResponse<byte[]> robotsTxt = client.send(robotsRequest.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (robotsTxt.statusCode() == 200) {
                return parser.parseContent(rootUrl.toString(),
                        robotsTxt.body(),
                        robotsTxt.headers().firstValue("Content-Type").orElse("text/plain"),
                        WmsaHome.getUserAgent().uaIdentifier());
            }
            else if (robotsTxt.statusCode() == 404) {
                return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
            }
        }
        catch (IOException ex) {
            logger.error("Error fetching robots.txt for {}: {} {}", rootUrl, ex.getClass().getSimpleName(), ex.getMessage());
        }
        return null;
    }

    /** Fetch a URL and store it in the database
     */
    private FetchResult fetchUrl(int domainId, EdgeUrl parsedUrl, CrawlDelayTimer timer, HttpClient client) throws Exception {

        timer.waitFetchDelay();

        HttpRequest request = HttpRequest.newBuilder(parsedUrl.asURI())
                .GET()
                .header("User-Agent", WmsaHome.getUserAgent().uaString())
                .header("Accept", "text/html")
                .timeout(readTimeout)
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Handle rate limiting by waiting and retrying once
            if (response.statusCode() == 429) {
                timer.waitRetryDelay(new HttpFetcherImpl.RateLimitException(
                        response.headers().firstValue("Retry-After").orElse("5")
                ));
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();

            if (response.statusCode() == 200) {
                if (!contentType.toLowerCase().startsWith("text/html")) {
                    return new FetchResult.Error(parsedUrl);
                }

                String body = response.body();
                if (body.length() > 1024 * 1024) {
                    return new FetchResult.Error(parsedUrl);
                }

                return new FetchResult.Success(domainId, parsedUrl, body, headersToString(response.headers()));
            }
        }
        catch (IOException ex) {
            // We don't want a full stack trace on every error, as it's quite common and very noisy
            logger.error("Error fetching URL {}: {} {}", parsedUrl, ex.getClass().getSimpleName(), ex.getMessage());
        }

        return new FetchResult.Error(parsedUrl);
    }

    sealed interface FetchResult {
        record Success(int domainId, EdgeUrl url, String body, String headers) implements FetchResult {}
        record Error(EdgeUrl url) implements FetchResult {}
    }

    private String headersToString(HttpHeaders headers) {
        StringBuilder headersStr = new StringBuilder();
        headers.map().forEach((k, v) -> {
            headersStr.append(k).append(": ").append(v).append("\n");
        });
        return headersStr.toString();
    }

    @Override
    public void close() throws Exception {
        pool.shutDown();
        for (int i = 0; i < 4; i++) {
            pool.awaitTermination(1, TimeUnit.HOURS);
        }
        pool.shutDownNow();
    }
}

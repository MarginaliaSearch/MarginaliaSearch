package nu.marginalia.livecrawler;

import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import nu.marginalia.WmsaHome;
import nu.marginalia.crawl.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.retreival.CrawlDelayTimer;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.util.SimpleBlockingThreadPool;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** A simple link scraper that fetches URLs and stores them in a database,
 * with no concept of a crawl frontier, WARC output, or other advanced features
 */
public class SimpleLinkScraper implements AutoCloseable {
    private final SimpleBlockingThreadPool pool = new SimpleBlockingThreadPool("LiveCrawler", 32, 10);
    private final LinkParser lp = new LinkParser();
    private final LiveCrawlDataSet dataSet;
    private final DbDomainQueries domainQueries;
    private final DomainBlacklist domainBlacklist;
    private final Duration connectTimeout = Duration.ofSeconds(10);
    private final Duration readTimeout = Duration.ofSeconds(10);

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
                .build()) {

            EdgeUrl rootUrl = domain.toRootUrlHttps();

            SimpleRobotRules rules = fetchRobotsRules(rootUrl, client);

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
                    continue;
                }

                fetchUrl(domainId, parsedUrl, timer, client);
            }
        }
    }

    private SimpleRobotRules fetchRobotsRules(EdgeUrl rootUrl, HttpClient client) throws IOException, InterruptedException, URISyntaxException {
        var robotsRequest = HttpRequest.newBuilder(rootUrl.withPathAndParam("/robots.txt", null).asURI())
                .GET()
                .header("User-Agent", WmsaHome.getUserAgent().uaString())
                .timeout(readTimeout);

        // Fetch the robots.txt

        SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
        SimpleRobotRules rules = new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
        HttpResponse<byte[]> robotsTxt = client.send(robotsRequest.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (robotsTxt.statusCode() == 200) {
            rules = parser.parseContent(rootUrl.toString(),
                    robotsTxt.body(),
                    robotsTxt.headers().firstValue("Content-Type").orElse("text/plain"),
                    WmsaHome.getUserAgent().uaIdentifier());
        }

        return rules;
    }

    private void fetchUrl(int domainId, EdgeUrl parsedUrl, CrawlDelayTimer timer, HttpClient client) throws Exception {

        timer.waitFetchDelay();

        // Loop for HTTP 429 retries
        for (int i = 0; i < 2; i++) {
            HttpRequest request = HttpRequest.newBuilder(parsedUrl.asURI())
                    .GET()
                    .header("User-Agent", WmsaHome.getUserAgent().uaString())
                    .header("Accept", "text/html")
                    .timeout(readTimeout)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                timer.waitRetryDelay(new HttpFetcherImpl.RateLimitException(
                        response.headers().firstValue("Retry-After").orElse("5")
                ));
                continue;
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();

            if (response.statusCode() == 200 && contentType.startsWith("text/html")) {
                dataSet.saveDocument(domainId, parsedUrl, response.body(), headersToString(response.headers()), "");
            }

            break;
        }
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

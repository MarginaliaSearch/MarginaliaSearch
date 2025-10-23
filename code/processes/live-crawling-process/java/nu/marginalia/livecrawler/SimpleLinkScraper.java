package nu.marginalia.livecrawler;

import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import nu.marginalia.WmsaHome;
import nu.marginalia.contenttype.ContentType;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.coordination.DomainLock;
import nu.marginalia.crawl.retreival.CrawlDelayTimer;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.util.SimpleBlockingThreadPool;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final DomainCoordinator domainCoordinator;

    private final static int MAX_SIZE = Integer.getInteger("crawler.maxFetchSize", 10 * 1024 * 1024);
    private final HttpClient httpClient;

    public SimpleLinkScraper(LiveCrawlDataSet dataSet,
                             DomainCoordinator domainCoordinator,
                             DbDomainQueries domainQueries,
                             HttpClient httpClient,
                             DomainBlacklist domainBlacklist) {
        this.dataSet = dataSet;
        this.domainCoordinator = domainCoordinator;
        this.domainQueries = domainQueries;
        this.domainBlacklist = domainBlacklist;
        this.httpClient = httpClient;
    }

    public void scheduleRetrieval(EdgeDomain domain, List<String> urls) {

        var id = domainQueries.tryGetDomainId(domain);
        if (id.isEmpty() || domainBlacklist.isBlacklisted(id.getAsInt())) {
            return;
        }

        pool.submitQuietly(() -> retrieveNow(domain, id.getAsInt(), urls));
    }

    public int retrieveNow(EdgeDomain domain, int domainId, List<String> urls) throws Exception {

        EdgeUrl rootUrl = domain.toRootUrlHttps();

        List<EdgeUrl> relevantUrls = new ArrayList<>(Math.max(1, urls.size()));

        // Resolve absolute URLs
        for (var url : urls) {
            Optional<EdgeUrl> optParsedUrl = lp.parseLink(rootUrl, url);

            if (optParsedUrl.isEmpty())
                continue;

            EdgeUrl absoluteUrl = optParsedUrl.get();

            if (!dataSet.hasUrl(absoluteUrl))
                relevantUrls.add(absoluteUrl);
        }

        if (relevantUrls.isEmpty()) {
            return 0;
        }

        int fetched = 0;

        try (// throttle concurrent access per domain; IDE will complain it's not used, but it holds a semaphore -- do not remove:
             DomainLock lock = domainCoordinator.lockDomain(domain)
        ) {
            SimpleRobotRules rules = fetchRobotsRules(rootUrl);

            if (rules == null) { // I/O error fetching robots.txt
                // If we can't fetch the robots.txt,
                for (var url : relevantUrls) {
                    maybeFlagAsBad(url);
                }
                return fetched;
            }

            CrawlDelayTimer timer = new CrawlDelayTimer(rules.getCrawlDelay());

            for (var parsedUrl : relevantUrls) {
                // Don't cross over to a different domain
                if (!Objects.equals(parsedUrl.getDomain(), domain))
                    continue;

                if (!rules.isAllowed(parsedUrl.toString())) {
                    maybeFlagAsBad(parsedUrl);
                    continue;
                }

                switch (fetchUrl(domainId, parsedUrl, timer)) {
                    case FetchResult.Success(int id, EdgeUrl docUrl, String body, String headers) -> {
                            dataSet.saveDocument(id, docUrl, body, headers, "");
                            fetched++;
                    }
                    case FetchResult.Error(EdgeUrl docUrl) -> {
                        maybeFlagAsBad(docUrl);
                    }
                }
            }
        }

        return fetched;
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
    private SimpleRobotRules fetchRobotsRules(EdgeUrl rootUrl) throws URISyntaxException {
        ClassicHttpRequest request = ClassicRequestBuilder.get(rootUrl.withPathAndParam("/robots.txt", null).asURI())
                .setHeader("User-Agent", WmsaHome.getUserAgent().uaString())
                .setHeader("Accept-Encoding", "gzip")
                .build();

        try {
            return httpClient.execute(request, rsp -> {
                if (rsp.getEntity() == null) {
                    return null;
                }
                try {
                    if (rsp.getCode() == 200) {
                        var contentTypeHeader = rsp.getFirstHeader("Content-Type");
                        if (contentTypeHeader == null) {
                            return null; // No content type header, can't parse
                        }
                        return new SimpleRobotRulesParser().parseContent(
                                rootUrl.toString(),
                                EntityUtils.toByteArray(rsp.getEntity()),
                                contentTypeHeader.getValue(),
                                WmsaHome.getUserAgent().uaIdentifier()
                        );
                    } else if (rsp.getCode() == 404) {
                        return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
                    }
                } finally {
                    EntityUtils.consumeQuietly(rsp.getEntity());
                }
                return null;
            });
        }
        catch (IOException e) {
            logger.error("Error fetching robots.txt for {}: {}", rootUrl, e.getMessage());
            return null; // I/O error fetching robots.txt
        }
        finally {
            try {
                TimeUnit.SECONDS.sleep(1);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    /** Fetch a URL and store it in the database
     */
    private FetchResult fetchUrl(int domainId, EdgeUrl parsedUrl, CrawlDelayTimer timer) throws Exception {

        ClassicHttpRequest request = ClassicRequestBuilder.get(parsedUrl.asURI())
                .setHeader("User-Agent", WmsaHome.getUserAgent().uaString())
                .setHeader("Accept", "text/html")
                .setHeader("Accept-Encoding", "gzip")
                .build();

        try {
            return httpClient.execute(request, rsp -> {
                try {
                    if (rsp.getCode() == 200) {
                        String contentType = rsp.getFirstHeader("Content-Type").getValue();
                        if (!contentType.toLowerCase().startsWith("text/html")) {
                            return new FetchResult.Error(parsedUrl);
                        }

                        byte[] body = EntityUtils.toByteArray(rsp.getEntity(), MAX_SIZE);

                        String bodyText = DocumentBodyToString.getStringData(ContentType.parse(contentType), body);

                        StringBuilder headersStr = new StringBuilder();
                        for (var header : rsp.getHeaders()) {
                            headersStr.append(header.getName()).append(": ").append(header.getValue()).append("\n");
                        }

                        return new FetchResult.Success(domainId, parsedUrl, bodyText, headersStr.toString());
                    }
                } finally {
                    if (rsp.getEntity() != null) {
                        EntityUtils.consumeQuietly(rsp.getEntity());
                    }
                }
                return new FetchResult.Error(parsedUrl);
            });
        }
        catch (IOException e) {
            logger.error("Error fetching {}: {}", parsedUrl, e.getMessage());
            // If we can't fetch the URL, we return an error result
            // so that the caller can decide what to do with it.
        }
        finally {
            timer.waitFetchDelay();
        }
        return new FetchResult.Error(parsedUrl);
    }

    sealed interface FetchResult {
        record Success(int domainId, EdgeUrl url, String body, String headers) implements FetchResult {}
        record Error(EdgeUrl url) implements FetchResult {}
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

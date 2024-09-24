package nu.marginalia.crawl.retreival;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.crawl.fetcher.HttpFetcher;
import nu.marginalia.ip_blocklist.IpBlockList;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawldata.CrawlerDomainStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.function.Predicate;

@Singleton
public class DomainProber {
    private final Logger logger = LoggerFactory.getLogger(DomainProber.class);
    private final Predicate<EdgeDomain> domainBlacklist;

    @Inject
    public DomainProber(IpBlockList ipBlockList) {
        this.domainBlacklist = ipBlockList::isAllowed;
    }

    /** For testing */
    public DomainProber(Predicate<EdgeDomain> domainBlacklist) {
        this.domainBlacklist = domainBlacklist;
    }

    /** To detect problems early we do a probing request to the domain before we start crawling it properly.
     *  This is a HEAD, typically to the root path.  We check the IP against the blocklist, we check that it
     *  doesn't immediately redirect to another domain (which should be crawled separately, not under the name
     *  of this domain).
     */
    public HttpFetcher.DomainProbeResult probeDomain(HttpFetcher fetcher, String domain, @Nullable EdgeUrl firstUrlInQueue) {

        if (firstUrlInQueue == null) {
            logger.warn("No valid URLs for domain {}", domain);

            return new HttpFetcher.DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "No known URLs");
        }

        if (!domainBlacklist.test(firstUrlInQueue.domain))
            return new HttpFetcher.DomainProbeResult.Error(CrawlerDomainStatus.BLOCKED, "IP not allowed");

        HttpFetcher.DomainProbeResult result;

        result = fetcher.probeDomain(firstUrlInQueue.withPathAndParam("/", null));

        // If the domain is not reachable over HTTPS, try HTTP
        if (result instanceof HttpFetcher.DomainProbeResult.Error) {
            if ("https".equalsIgnoreCase(firstUrlInQueue.proto)) {
                firstUrlInQueue = new EdgeUrl(
                        "http",
                        firstUrlInQueue.domain,
                        firstUrlInQueue.port,
                        firstUrlInQueue.path,
                        firstUrlInQueue.param
                );

                result = fetcher.probeDomain(firstUrlInQueue.withPathAndParam("/", null));
            }
        }

        return result;
    }

}

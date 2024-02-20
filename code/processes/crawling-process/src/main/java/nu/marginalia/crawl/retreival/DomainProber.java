package nu.marginalia.crawl.retreival;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.crawl.retreival.fetcher.FetchResultState;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawling.model.CrawlerDomainStatus;
import nu.marginalia.ip_blocklist.IpBlockList;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
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
    public ProbeResult probeDomain(HttpFetcher fetcher, String domain, @Nullable EdgeUrl firstUrlInQueue) {

        if (firstUrlInQueue == null) {
            logger.warn("No valid URLs for domain {}", domain);

            return new ProbeResultError(CrawlerDomainStatus.ERROR, "No known URLs");
        }

        if (!domainBlacklist.test(firstUrlInQueue.domain))
            return new ProbeResultError(CrawlerDomainStatus.BLOCKED, "IP not allowed");

        var fetchResult = fetcher.probeDomain(firstUrlInQueue.withPathAndParam("/", null));

        if (fetchResult.ok())
            return new ProbeResultOk(fetchResult.url);

        if (fetchResult.state == FetchResultState.REDIRECT)
            return new ProbeResultRedirect(fetchResult.domain);

        return new ProbeResultError(CrawlerDomainStatus.ERROR, "Bad status");
    }

    public sealed interface ProbeResult permits ProbeResultError, ProbeResultRedirect, ProbeResultOk {}

    /** The probing failed for one reason or another
     * @param status  Machine readable status
     * @param desc   Human-readable description of the error
     */
    public record ProbeResultError(CrawlerDomainStatus status, String desc) implements ProbeResult {}

    /** This domain redirects to another domain */
    public record ProbeResultRedirect(EdgeDomain domain) implements ProbeResult {}

    /** If the retrieval of the probed url was successful, return the url as it was fetched
     * (which may be different from the url we probed, if we attempted another URL schema).
     *
     * @param probedUrl  The url we successfully probed
     */
    public record ProbeResultOk(EdgeUrl probedUrl) implements ProbeResult {}
}

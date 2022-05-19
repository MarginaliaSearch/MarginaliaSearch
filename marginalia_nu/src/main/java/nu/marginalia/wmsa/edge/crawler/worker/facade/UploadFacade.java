package nu.marginalia.wmsa.edge.crawler.worker.facade;

import com.google.inject.ImplementedBy;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainLink;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageContent;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlVisit;

import java.util.Collection;

@ImplementedBy(UploadFacadeDirectImpl.class)
public interface UploadFacade {
    void putLinks(Collection<EdgeDomainLink> links, boolean wipeExisting);
    void putUrls(Collection<EdgeUrl> urls, double quality);
    void putFeeds(Collection<EdgeUrl> urls);
    void putUrlVisits(Collection<EdgeUrlVisit> visits);
    void putDomainAlias(EdgeDomain src, EdgeDomain dst);
    void finishTask(EdgeDomain domain, double quality, EdgeDomainIndexingState state);

    void putWords(Collection<EdgePageContent> pages, int writer);

    boolean isBlacklisted(EdgeDomain domain);
    boolean isBlocked();
}

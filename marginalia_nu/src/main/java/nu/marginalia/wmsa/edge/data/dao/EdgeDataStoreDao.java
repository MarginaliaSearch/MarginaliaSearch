package nu.marginalia.wmsa.edge.data.dao;

import com.google.inject.ImplementedBy;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@ImplementedBy(EdgeDataStoreDaoImpl.class)
public interface EdgeDataStoreDao {
    boolean isBlacklisted(EdgeDomain domain);

    EdgeId<EdgeDomain> getDomainId(EdgeDomain domain);

    List<BrowseResult> getDomainNeighborsAdjacent(EdgeId<EdgeDomain> domainId, EdgeDomainBlacklist backlist, int count);
    List<BrowseResult> getRandomDomains(int count, EdgeDomainBlacklist backlist);
    List<EdgeUrlDetails> getUrlDetailsMulti(List<EdgeId<EdgeUrl>> ids);


    EdgeDomain getDomain(EdgeId<EdgeDomain> id);

    Optional<EdgeId<EdgeUrl>> resolveAmbiguousDomain(String name);


    int getPagesKnown(EdgeId<EdgeDomain> domainId);
    int getPagesVisited(EdgeId<EdgeDomain> domainId);
    int getPagesIndexed(EdgeId<EdgeDomain> domainId);

    int getIncomingLinks(EdgeId<EdgeDomain> domainId);
    int getOutboundLinks(EdgeId<EdgeDomain> domainId);

    double getDomainQuality(EdgeId<EdgeDomain> domainId);

    EdgeDomainIndexingState getDomainState(EdgeId<EdgeDomain> domainId);

    List<EdgeDomain> getLinkingDomains(EdgeId<EdgeDomain> domainId);

    double getRank(EdgeId<EdgeDomain> domainId);

}

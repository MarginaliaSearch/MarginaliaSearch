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
    EdgeId<EdgeDomain> getDomainId(EdgeDomain domain);

    List<BrowseResult> getDomainNeighborsAdjacent(EdgeId<EdgeDomain> domainId, EdgeDomainBlacklist backlist, int count);

    List<BrowseResult> getRandomDomains(int count, EdgeDomainBlacklist backlist);
    List<EdgeUrlDetails> getUrlDetailsMulti(List<EdgeId<EdgeUrl>> ids);

    EdgeDomain getDomain(EdgeId<EdgeDomain> id);


}

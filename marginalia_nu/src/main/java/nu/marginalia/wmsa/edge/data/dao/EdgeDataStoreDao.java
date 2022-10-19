package nu.marginalia.wmsa.edge.data.dao;

import com.google.inject.ImplementedBy;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeId;
import nu.marginalia.wmsa.edge.model.id.EdgeIdCollection;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;

import java.util.List;
import java.util.Optional;

@ImplementedBy(EdgeDataStoreDaoImpl.class)
public interface EdgeDataStoreDao {
    EdgeId<EdgeDomain> getDomainId(EdgeDomain domain);

    List<BrowseResult> getDomainNeighborsAdjacent(EdgeId<EdgeDomain> domainId, EdgeDomainBlacklist backlist, int count);

    List<BrowseResult> getRandomDomains(int count, EdgeDomainBlacklist backlist, int set);

    List<BrowseResult> getBrowseResultFromUrlIds(EdgeIdCollection<EdgeUrl> urlId);

    List<EdgeUrlDetails> getUrlDetailsMulti(EdgeIdCollection<EdgeUrl> ids);

    Optional<EdgeDomain> getDomain(EdgeId<EdgeDomain> id);


}

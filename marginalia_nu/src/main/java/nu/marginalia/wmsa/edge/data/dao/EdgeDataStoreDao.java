package nu.marginalia.wmsa.edge.data.dao;

import com.google.inject.ImplementedBy;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;

import java.util.List;

@ImplementedBy(EdgeDataStoreDaoImpl.class)
public interface EdgeDataStoreDao {
    EdgeId<EdgeDomain> getDomainId(EdgeDomain domain);

    List<BrowseResult> getDomainNeighborsAdjacent(EdgeId<EdgeDomain> domainId, EdgeDomainBlacklist backlist, int count);

    List<BrowseResult> getRandomDomains(int count, EdgeDomainBlacklist backlist);

    List<BrowseResult> getBrowseResultFromUrlIds(List<EdgeId<EdgeUrl>> urlId, int count);

    List<EdgeUrlDetails> getUrlDetailsMulti(List<EdgeId<EdgeUrl>> ids);

    EdgeDomain getDomain(EdgeId<EdgeDomain> id);


}

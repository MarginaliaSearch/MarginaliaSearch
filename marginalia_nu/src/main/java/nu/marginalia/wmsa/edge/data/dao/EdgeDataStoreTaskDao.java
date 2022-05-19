package nu.marginalia.wmsa.edge.data.dao;

import com.google.inject.ImplementedBy;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDataStoreTaskDaoImpl;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeIndexTask;


@ImplementedBy(EdgeDataStoreTaskDaoImpl.class)
public interface EdgeDataStoreTaskDao {
    EdgeIndexTask getIndexTask(int pass, int limit);
    EdgeIndexTask getDiscoverTask();
    void finishIndexTask(EdgeDomain domain, double quality, EdgeDomainIndexingState state);
    void finishBadIndexTask(EdgeDomain domain, EdgeDomainIndexingState state);
    void flushOngoingJobs();
    boolean isBlocked();
}

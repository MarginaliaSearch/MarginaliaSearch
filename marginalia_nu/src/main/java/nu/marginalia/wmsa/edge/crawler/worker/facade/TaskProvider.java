package nu.marginalia.wmsa.edge.crawler.worker.facade;

import com.google.inject.ImplementedBy;
import nu.marginalia.wmsa.edge.model.crawl.EdgeIndexTask;

@ImplementedBy(TaskProviderImpl.class)
public interface TaskProvider {
    EdgeIndexTask getIndexTask(int pass);
    EdgeIndexTask getDiscoverTask();
}

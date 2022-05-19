package nu.marginalia.wmsa.edge.crawler.worker.facade;

import nu.marginalia.wmsa.client.exception.RouteNotConfiguredException;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.director.client.EdgeDirectorClient;
import nu.marginalia.wmsa.edge.model.crawl.EdgeIndexTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class TaskProviderImpl implements TaskProvider {

    private final EdgeDirectorClient client;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public TaskProviderImpl(EdgeDirectorClient client) {
        this.client = client;
    }

    @Override
    public EdgeIndexTask getIndexTask(int pass) {
        try {
            return client.getIndexTask(Context.internal(), pass, 100)
                    .onErrorReturn(t -> new EdgeIndexTask(null, 0, 0, 1.))
                    .blockingFirst();
        }
        catch (RouteNotConfiguredException ex) {
            logger.warn("No route to Director");
            return new EdgeIndexTask(null, 0, 0, 1.);
        }
    }

    @Override
    public EdgeIndexTask getDiscoverTask() {
        try {
            return client.getDiscoverTask(Context.internal())
                    .onErrorReturn(t -> new EdgeIndexTask(null, 0, 0, 1.))
                    .blockingFirst();
        }
        catch (RouteNotConfiguredException ex) {
            logger.warn("No route to Data Store");
            return new EdgeIndexTask(null, 0, 0, 1.);
        }
    }
}

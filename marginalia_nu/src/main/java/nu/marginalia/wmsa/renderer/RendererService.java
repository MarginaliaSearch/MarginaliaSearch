package nu.marginalia.wmsa.renderer;


import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.resource_store.ResourceStoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RendererService extends Service {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();

    private final ResourceStoreClient resourceStoreClient;


    @Inject
    public RendererService(ResourceStoreClient resourceStoreClient,
                           @Named("service-host") String ip,
                           @Named("service-port") Integer port,
                           SmhiRendererService smhiRendererService,
                           PodcastRendererService podcastRendererService,
                           StatusRendererService statusRendererService,
                           Initialization initialization,
                           MetricsServer metricsServer
                           ) {
        super(ip, port, initialization, metricsServer);

        this.resourceStoreClient = resourceStoreClient;

        smhiRendererService.start();
        podcastRendererService.start();
        statusRendererService.start();
    }

    public boolean isReady() {
        return resourceStoreClient.isAccepting();
    }

}

package nu.marginalia.wmsa.renderer;


import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.resource_store.ResourceStoreClient;


public class RendererService extends Service {
    private final ResourceStoreClient resourceStoreClient;


    @Inject
    public RendererService(ResourceStoreClient resourceStoreClient,
                           @Named("service-host") String ip,
                           @Named("service-port") Integer port,
                           PodcastRendererService podcastRendererService,
                           StatusRendererService statusRendererService,
                           Initialization initialization,
                           MetricsServer metricsServer
                           ) {
        super(ip, port, initialization, metricsServer);

        this.resourceStoreClient = resourceStoreClient;

        podcastRendererService.start();
        statusRendererService.start();
    }

    public boolean isReady() {
        return resourceStoreClient.isAccepting();
    }

}

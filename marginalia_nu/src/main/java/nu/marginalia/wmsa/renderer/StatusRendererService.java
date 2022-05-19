package nu.marginalia.wmsa.renderer;

import com.google.inject.Inject;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import nu.marginalia.wmsa.resource_store.ResourceStoreClient;
import nu.marginalia.wmsa.resource_store.model.RenderedResource;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class StatusRendererService {
    private final MustacheRenderer<Object> statusRenderer;
    private ResourceStoreClient resourceStoreClient;

    private final OkHttpClient client;

    private final RendererFactory rendererFactory = new RendererFactory();

    @Inject
    @SneakyThrows
    public StatusRendererService(ResourceStoreClient resourceStoreClient) {
        this.resourceStoreClient = resourceStoreClient;

        client = new OkHttpClient.Builder()
                .connectTimeout(50, TimeUnit.MILLISECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .build();
        statusRenderer = rendererFactory.renderer( "status/server-status");
    }

    public void start() {
        Schedulers.io().schedulePeriodicallyDirect(this::renderStatusPage, 1, 60, TimeUnit.SECONDS);
    }
    public void renderStatusPage() {
        try {
            var status = getStatus();
            var page = statusRenderer.render(Map.of("status", status));
            resourceStoreClient
                    .putResource(Context.internal(), "status",
                            new RenderedResource("index.html", LocalDateTime.now().plus(2, ChronoUnit.MINUTES), page))
                    .blockingSubscribe();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<ServerStatusModel> getStatus() {
        List<ServerStatusModel> status = new ArrayList<>(ServiceDescriptor.values().length);

        for (ServiceDescriptor sd : ServiceDescriptor.values()) {
            if (sd.port == 0) {
                continue;
            }
            try {
                var req = new Request.Builder().url("http://127.0.0.1:" + sd.port + "/internal/ping").get().build();
                var call = client.newCall(req);

                call.execute().close();
                status.add(new ServerStatusModel(sd.name, "UP"));

            } catch (Exception e) {
                status.add(new ServerStatusModel(sd.name, "DOWN"));
            }
        }
        return status;
    }

}

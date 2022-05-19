package nu.marginalia.wmsa.smhi;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.renderer.client.RendererClient;
import nu.marginalia.wmsa.renderer.request.smhi.RenderSmhiIndexReq;
import nu.marginalia.wmsa.renderer.request.smhi.RenderSmhiPrognosReq;
import nu.marginalia.wmsa.smhi.model.Plats;
import nu.marginalia.wmsa.smhi.model.PrognosData;
import nu.marginalia.wmsa.smhi.scraper.crawler.SmhiCrawler;
import nu.marginalia.wmsa.smhi.scraper.crawler.entity.SmhiEntityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SmhiScraperService extends Service {

    private final SmhiCrawler crawler;
    private final SmhiEntityStore entityStore;
    private final RendererClient rendererClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Initialization initialization;
    @Inject
    public SmhiScraperService(@Named("service-host") String ip,
                              @Named("service-port") Integer port,
                              SmhiCrawler crawler,
                              SmhiEntityStore entityStore,
                              RendererClient rendererClient,
                              Initialization initialization,
                              MetricsServer metricsServer) {
        super(ip, port, initialization, metricsServer);
        this.crawler = crawler;
        this.entityStore = entityStore;
        this.rendererClient = rendererClient;
        this.initialization = initialization;

        Spark.awaitInitialization();

        Schedulers.newThread().scheduleDirect(this::start);
    }

    private void start() {
            initialization.waitReady();
            rendererClient.waitReady();

            entityStore.platser.debounce(6, TimeUnit.SECONDS)
                    .subscribe(this::updateIndex);
            entityStore.prognosdata.subscribe(this::updatePrognos);

            crawler.start();
    }

    private void updatePrognos(PrognosData prognosData) {
        rendererClient
                .render(Context.internal(), new RenderSmhiPrognosReq(prognosData))
                .timeout(30, TimeUnit.SECONDS)
                .blockingSubscribe();
    }

    private void updateIndex(Plats unused) {
        var platser = entityStore.platser().stream()
                .sorted(Comparator.comparing(plats -> plats.namn))
                .collect(Collectors.toList());

        rendererClient
                .render(Context.internal(), new RenderSmhiIndexReq(platser))
                .timeout(30, TimeUnit.SECONDS)
                .blockingSubscribe();
    }
}

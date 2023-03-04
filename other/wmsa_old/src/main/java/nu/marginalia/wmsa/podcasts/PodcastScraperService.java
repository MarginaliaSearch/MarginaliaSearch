package nu.marginalia.wmsa.podcasts;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.client.Context;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.MetricsServer;
import nu.marginalia.service.server.Service;
import nu.marginalia.wmsa.podcasts.model.Podcast;
import nu.marginalia.wmsa.podcasts.model.PodcastEpisode;
import nu.marginalia.wmsa.renderer.client.RendererClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PodcastScraperService extends Service {

    private final Map<String, String> podcastUrls = Map.of(
            "SBS", "https://feeds.simplecast.com/Fxu1mrhe",
            "hopwag", "https://feed.podbean.com/hopwag/feed.xml",
            "philosophizethis", "https://philosophizethis.libsyn.com/rss",
            "PEL", "https://partiallyexaminedlife.libsyn.com/rss",
            "IOT", "https://podcasts.files.bbci.co.uk/b006qykl.rss",
            "SaturaLanx", "https://anchor.fm/s/2c536214/podcast/rss",
            "ControversiesInChurchHistory", "https://anchor.fm/s/9b43760/podcast/rss",
            "readmeapoem", "https://rss.acast.com/readmeapoem",
            "HoL", "https://feeds.megaphone.fm/history-of-literature",
            "Revolutions", "https://revolutionspodcast.libsyn.com/rss"
    );

    private final RendererClient rendererClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Initialization initialization;

    @Inject
    public PodcastScraperService(@Named("service-host") String ip,
                              @Named("service-port") Integer port,
                              RendererClient rendererClient,
                              Initialization initialization,
                              MetricsServer metricsServer) {
        super(ip, port, initialization, metricsServer);
        this.rendererClient = rendererClient;
        this.initialization = initialization;

        Spark.awaitInitialization();

        Schedulers.io().schedulePeriodicallyDirect(this::fetchPods, 0, 1, TimeUnit.HOURS);
    }

    private void fetchPods() {
        try {
            PodcastFetcher fetcher = new PodcastFetcher();

            podcastUrls.forEach(fetcher::fetchPodcast);

            rendererClient.render(Context.internal("podcast"), fetcher.getNewEpisodes()).blockingSubscribe();
            rendererClient.render(Context.internal("podcast"), fetcher.getListing()).blockingSubscribe();

            for (Podcast podcast : fetcher.getAllPodcasts()) {
                rendererClient.render(Context.internal("podcast"), podcast).blockingSubscribe();
            }
            for (PodcastEpisode episode : fetcher.getAllEpisodes()) {
                rendererClient.render(Context.internal("podcast"), episode).blockingSubscribe();
            }
        }
        catch (RuntimeException ex) {
            logger.error("Uncaught exception", ex);
        }
    }

    public void start() {
        logger.info("Started");
    }
}
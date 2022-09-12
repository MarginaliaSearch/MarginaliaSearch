package nu.marginalia.wmsa.renderer;

import com.google.gson.Gson;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.podcasts.model.Podcast;
import nu.marginalia.wmsa.podcasts.model.PodcastEpisode;
import nu.marginalia.wmsa.podcasts.model.PodcastListing;
import nu.marginalia.wmsa.podcasts.model.PodcastNewEpisodes;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import nu.marginalia.wmsa.resource_store.ResourceStoreClient;
import nu.marginalia.wmsa.resource_store.model.RenderedResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class PodcastRendererService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();

    private final RendererFactory rendererFactory = new RendererFactory();

    private final MustacheRenderer<PodcastNewEpisodes> newsRenderer;
    private final MustacheRenderer<PodcastEpisode> episodeRenderer;
    private final MustacheRenderer<PodcastListing> listingRenderer;
    private final MustacheRenderer<Podcast> podcastRenderer;

    private final ResourceStoreClient resourceStoreClient;


    @Inject @SneakyThrows
    public PodcastRendererService(ResourceStoreClient resourceStoreClient) {
        this.resourceStoreClient = resourceStoreClient;
        newsRenderer = rendererFactory.renderer( "podcast/new");
        episodeRenderer = rendererFactory.renderer( "podcast/episode");
        listingRenderer = rendererFactory.renderer( "podcast/listing");
        podcastRenderer = rendererFactory.renderer( "podcast/podcast");
    }

    public void start() {
        Spark.post("/render/podcast", this::renderPodcast);
        Spark.post("/render/podcast/episode", this::renderPodcastEpisode);
        Spark.post("/render/podcast/new", this::renderPodcastNew);
        Spark.post("/render/podcast/listing", this::renderPodcastListing);
    }

    private Object renderPodcastListing(Request request, Response response) {
        var requestText = request.body();
        var req = gson.fromJson(requestText, PodcastListing.class);

        logger.info("renderPodcastListing()");

        var resource = new RenderedResource("list.html",
                getRetentionTime(),
                listingRenderer.render(req));

        storeResource(request, resource);

        return "";
    }


    private Object renderPodcast(Request request, Response response) {
        var requestText = request.body();
        var req = gson.fromJson(requestText, Podcast.class);

        logger.info("renderPodcast({})", req.metadata.id);

        var resource = new RenderedResource(req.metadata.id+".html",
                getRetentionTime(),
                podcastRenderer.render(req));

        storeResource(request, resource);

        return "";
    }

    private Object renderPodcastEpisode(Request request, Response response) {
        var requestText = request.body();
        var req = gson.fromJson(requestText, PodcastEpisode.class);
        Context.fromRequest(request);

        logger.info("renderPodcastEpisode({}/{})", req.podcastName, req.guid);
        var resource = new RenderedResource(req.guid+".html",
                getRetentionTime(),
                episodeRenderer.render(req));

        storeResource(request, resource);

        return "";
    }

    private Object renderPodcastNew(Request request, Response response) {
        var requestText = request.body();
        var req = gson.fromJson(requestText, PodcastNewEpisodes.class);

        logger.info("renderPodcastNew()");

        var resource = new RenderedResource("new.html",
                getRetentionTime(),
                newsRenderer.render(req));

        storeResource(request, resource);

        return "";
    }


    private LocalDateTime getRetentionTime() {
        return LocalDateTime.now().plus(24, ChronoUnit.HOURS);
    }

    private void storeResource(Request request, RenderedResource resource) {
        resourceStoreClient.putResource(Context.fromRequest(request), "podcast", resource)
                .timeout(10, TimeUnit.SECONDS)
                .blockingSubscribe();
    }


}

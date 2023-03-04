package nu.marginalia.wmsa.renderer.client;

import com.google.gson.Gson;
import io.reactivex.rxjava3.core.Observable;
import lombok.SneakyThrows;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.Context;
import nu.marginalia.client.HttpStatusCode;
import nu.marginalia.service.descriptor.HostsFile;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.wmsa.podcasts.model.Podcast;
import nu.marginalia.wmsa.podcasts.model.PodcastEpisode;
import nu.marginalia.wmsa.podcasts.model.PodcastListing;
import nu.marginalia.wmsa.podcasts.model.PodcastNewEpisodes;
import nu.marginalia.client.exception.TimeoutException;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;


public class RendererClient extends AbstractDynamicClient {
    @Inject
    public RendererClient(ServiceDescriptors descriptors) {
        super(descriptors.forId(ServiceId.Index), new HostsFile(), Gson::new);
    }

    @SneakyThrows
    public Observable<HttpStatusCode> render(Context ctx, PodcastNewEpisodes req) {
        return post(ctx, "/render/podcast/new", req)
                .timeout(5, TimeUnit.SECONDS, Observable.error(new TimeoutException("RendererClient.renderPodcastNew()")));
    }


    @SneakyThrows
    public Observable<HttpStatusCode> render(Context ctx, PodcastEpisode req) {
        return post(ctx, "/render/podcast/episode", req)
                .timeout(5, TimeUnit.SECONDS, Observable.error(new TimeoutException("RendererClient.renderPodcastEpisode()")));
    }

    @SneakyThrows
    public Observable<HttpStatusCode> render(Context ctx, PodcastListing req) {
        return post(ctx, "/render/podcast/listing", req)
                .timeout(5, TimeUnit.SECONDS, Observable.error(new TimeoutException("RendererClient.renderPodcastListing()")));
    }


    @SneakyThrows
    public Observable<HttpStatusCode> render(Context ctx, Podcast req) {
        return post(ctx, "/render/podcast", req)
                .timeout(5, TimeUnit.SECONDS, Observable.error(new TimeoutException("RendererClient.renderPodcastEpisode()")));
    }
}

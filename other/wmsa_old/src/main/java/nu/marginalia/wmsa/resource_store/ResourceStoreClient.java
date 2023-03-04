package nu.marginalia.wmsa.resource_store;

import com.google.gson.Gson;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.Context;
import nu.marginalia.client.HttpStatusCode;
import nu.marginalia.service.descriptor.HostsFile;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.wmsa.resource_store.model.RenderedResource;
import nu.marginalia.client.exception.TimeoutException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Singleton
public class ResourceStoreClient extends AbstractDynamicClient {

    @Inject
    public ResourceStoreClient(ServiceDescriptors descriptors) {
        super(descriptors.forId(ServiceId.Other_ResourceStore), new HostsFile(), Gson::new);
    }

    public Observable<String> getResource(Context ctx, String domain, String resource) {
        return get(ctx, "/"+domain+"/"+resource)
                .timeout(5, TimeUnit.SECONDS, Observable.error(new TimeoutException("ResourceStoreClient.getResource()")))
                ;
    }

    public Observable<HttpStatusCode> putResource(Context ctx, String domain, RenderedResource data) {
        return post(ctx, "/"+domain, data)
                .timeout(5, TimeUnit.SECONDS, Observable.error(new TimeoutException("ResourceStoreClient.putResource()")));

    }

    public Observable<String> cacheResource(Context ctx, String domain, String resource, Supplier<String> generator, LocalDateTime expiry) {
        return getResource(ctx, domain, resource)
            .onErrorReturn(e -> {
                var renderedResource = new RenderedResource(resource, expiry, generator.get());
                putResource(ctx, "wiki", renderedResource).subscribeOn(Schedulers.io()).blockingSubscribe();
                return renderedResource.data;
            });
    }
}

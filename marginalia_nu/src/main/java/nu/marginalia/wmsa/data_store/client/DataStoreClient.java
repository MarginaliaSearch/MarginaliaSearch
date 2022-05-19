package nu.marginalia.wmsa.data_store.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.reactivex.rxjava3.core.Observable;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.client.HttpStatusCode;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.data_store.meta.DomainInformation;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainLink;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlVisit;
import org.eclipse.jetty.util.UrlEncoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.inject.Inject;
import java.util.List;

public class DataStoreClient extends AbstractDynamicClient {
    private final Gson gson = new GsonBuilder()
            .create();

    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Inject
    public DataStoreClient() {
        super(ServiceDescriptor.DATA_STORE);
    }

    @CheckReturnValue
    public <T> Observable<T> getJson(Context ctx, Class<T> type, String domain, String resource) {
        var route = "/data/"+domain+"/"+type.getSimpleName()+"/"+resource;

        return super.get(ctx, route, type);
    }

    @CheckReturnValue
    @SuppressWarnings("unchecked")
    public <T> Observable<List<String>> getJsonIndicies(Context ctx, Class<T> type, String domain) {
        var route = "/data/"+domain+"/"+type.getSimpleName();

        return super.get(ctx, route)
                .map(data -> (List<String>) gson.fromJson(data, List.class))
                ;
    }

    @CheckReturnValue
    @SneakyThrows
    public <T> Observable<HttpStatusCode> offerJson(Context ctx, Class<? super T> type, T object, String domain, String resource) {
        var route = "/data/"+domain+"/"+type.getSimpleName()+"/"+resource;
        return super.post(ctx, route, object)
                ;
    }
    @CheckReturnValue
    @Deprecated
    public Observable<HttpStatusCode> putLink(Context ctx, EdgeDomainLink... data) {
        return super.post(ctx, "/edge/link", data);
    }
    @CheckReturnValue
    @Deprecated
    public Observable<HttpStatusCode> putUrl(Context ctx, double quality, EdgeUrl... data) {
        return super.post(ctx, "/edge/url?quality="+quality, data);
    }
    @CheckReturnValue
    @Deprecated
    public Observable<HttpStatusCode> putUrlVisited(Context ctx, EdgeUrlVisit... data) {
        return super.post(ctx, "/edge/url-visited", data);
    }
    @CheckReturnValue
    @Deprecated
    public Observable<HttpStatusCode> putDomainAlias(Context ctx, EdgeDomain source, EdgeDomain dest) {
        var srcEnc = UrlEncoded.encodeString(source.toString());
        var dstEnc = UrlEncoded.encodeString(dest.toString());

        return super.post(ctx, "/edge/domain-alias/" + srcEnc + "/" + dstEnc, "");
    }



    @CheckReturnValue
    public Observable<EdgeUrl> getUrl(Context ctx, EdgeId<EdgeUrl> url) {
        return super.get(ctx, "/edge/url/"+url.getId(), EdgeUrl.class);
    }

    @CheckReturnValue
    @SuppressWarnings("unchecked")
    public Observable<EdgeId<EdgeDomain>> getDomainId(Context ctx, EdgeDomain domain) {
        var dom = UrlEncoded.encodeString(domain.toString());

        return super.get(ctx, "/edge/domain-id/"+dom, EdgeId.class)
                .map(id -> (EdgeId<EdgeDomain>) id);
    }


    @CheckReturnValue
    public Observable<EdgeDomain> getDomain(Context ctx, EdgeId<EdgeDomain> url) {
        return super.get(ctx, "/edge/domain/"+url.getId(), EdgeDomain.class);
    }

    public Observable<DomainInformation> siteInfo(Context ctx, String site) {
        return super.get(ctx,"/edge/meta/" + UrlEncoded.encodeString(site), DomainInformation.class);
    }
}

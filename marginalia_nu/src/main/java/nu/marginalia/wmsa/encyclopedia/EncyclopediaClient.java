package nu.marginalia.wmsa.encyclopedia;

import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.client.HttpStatusCode;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.assistant.dict.WikiArticles;
import okhttp3.MediaType;
import org.eclipse.jetty.util.UrlEncoded;

import javax.annotation.CheckReturnValue;

public class EncyclopediaClient extends AbstractDynamicClient {
    public EncyclopediaClient() {
        super(ServiceDescriptor.ENCYCLOPEDIA);
    }

    @CheckReturnValue
    public Observable<HttpStatusCode> submitWiki(Context ctx, String url, String data) {
        return super.post(ctx, "/wiki/submit?url="+UrlEncoded.encodeString(url), data, MediaType.parse("text/plain; charset=UTF-8"));
    }

    @CheckReturnValue
    public Observable<Boolean> hasWiki(Context ctx, String url) {
        return super.get(ctx, "/wiki/has?url="+ UrlEncoded.encodeString(url), Boolean.class);
    }

    @CheckReturnValue
    public Observable<WikiArticles> encyclopediaLookup(Context ctx, String word) {
        return super.get(ctx,"/encyclopedia/" + UrlEncoded.encodeString(word), WikiArticles.class);
    }

}

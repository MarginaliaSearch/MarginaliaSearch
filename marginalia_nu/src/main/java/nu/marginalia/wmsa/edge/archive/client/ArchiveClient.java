package nu.marginalia.wmsa.edge.archive.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.client.HttpStatusCode;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.archive.request.EdgeArchiveSubmissionReq;
import nu.marginalia.wmsa.edge.model.crawl.EdgeRawPageContents;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import okhttp3.MediaType;
import org.eclipse.jetty.util.UrlEncoded;

import javax.annotation.CheckReturnValue;
import java.util.concurrent.Semaphore;

@Singleton
public class ArchiveClient extends AbstractDynamicClient {

    private final Semaphore submitPageSem = new Semaphore(3, true);

    @Inject
    public ArchiveClient() {
        super(ServiceDescriptor.EDGE_ARCHIVE);
    }

    @CheckReturnValue
    public void submitPage(Context ctx, EdgeUrl url, EdgeRawPageContents data) throws InterruptedException {
        try {
            submitPageSem.acquire();
            super.post(ctx, "/page/submit", new EdgeArchiveSubmissionReq(url, data)).blockingSubscribe();
        }
        finally {
            submitPageSem.release();
        }

    }

    @CheckReturnValue
    public Observable<HttpStatusCode> submitWiki(Context ctx, String url, String data) {
        return super.post(ctx, "/wiki/submit?url="+UrlEncoded.encodeString(url), data, MediaType.parse("text/plain; charset=UTF-8"));
    }

    @CheckReturnValue
    public Observable<Boolean> hasWiki(Context ctx, String url) {
        return super.get(ctx, "/wiki/has?url="+UrlEncoded.encodeString(url), Boolean.class);
    }

    @CheckReturnValue
    public Observable<String> getWiki(Context ctx, String url) {
        return super.get(ctx, "/wiki/get?url="+UrlEncoded.encodeString(url));
    }

}

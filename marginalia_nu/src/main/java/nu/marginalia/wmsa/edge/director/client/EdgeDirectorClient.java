package nu.marginalia.wmsa.edge.director.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.client.HttpStatusCode;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeIndexTask;
import org.eclipse.jetty.util.UrlEncoded;

import javax.annotation.CheckReturnValue;

@Singleton
public class EdgeDirectorClient extends AbstractDynamicClient {

    @Inject
    public EdgeDirectorClient() {
        super(ServiceDescriptor.EDGE_DIRECTOR);
    }

    @CheckReturnValue
    public Observable<EdgeIndexTask> getIndexTask(Context ctx, int pass, int limit) {
        return super.get(ctx, "/edge/task/index/"+pass+"?limit="+limit, EdgeIndexTask.class);
    }
    @CheckReturnValue
    public Observable<EdgeIndexTask> getDiscoverTask(Context ctx) {
        return super.get(ctx, "/edge/task/discover/", EdgeIndexTask.class);
    }

    @CheckReturnValue
    public Observable<HttpStatusCode> finishTask(Context ctx, EdgeDomain domain, double quality, EdgeDomainIndexingState state) {
        return super.delete(ctx, "/edge/task/"+ UrlEncoded.encodeString(domain.toString())+"?quality="+quality+"&state="+state.toString());
    }

    @CheckReturnValue
    public Observable<Boolean> isBlocked(Context ctx) {
        return super.get(ctx, "/edge/task/blocked", Boolean.class);
    }

    @CheckReturnValue
    public void flushOngoingJobs(Context ctx) {
        super.post(ctx, "/edge/task/flush", new Object()).blockingSubscribe();
    }

}

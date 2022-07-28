package nu.marginalia.wmsa.edge.index.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.client.HttpStatusCode;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.index.model.EdgePutWordsRequest;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultSet;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSpecification;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchResults;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class EdgeIndexClient extends AbstractDynamicClient {
    private final Gson gson = new GsonBuilder()
            .create();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public EdgeIndexClient() {
        super(ServiceDescriptor.EDGE_INDEX);
        setTimeout(30);
    }

    @CheckReturnValue
    public Observable<HttpStatusCode> putWords(Context ctx, EdgeId<EdgeDomain> domain, EdgeId<EdgeUrl> url, double quality,
                                               EdgePageWordSet wordSet, int writer
                                               )
    {
        EdgePutWordsRequest request = new EdgePutWordsRequest(domain, url, quality, wordSet, writer);

        return this.post(ctx, "/words/", request);
    }


    @CheckReturnValue
    public EdgeSearchResultSet query(Context ctx, EdgeSearchSpecification specs) {
        return this.postGet(ctx, "/search/", specs, EdgeSearchResultSet.class).blockingFirst();
    }

    @CheckReturnValue
    public List<EdgeSearchResultSet> multiQuery(Context ctx, EdgeSearchSpecification... specs) {

        return Observable.fromArray(specs)
                .concatMap(s -> postGet(ctx, "/search/", s, EdgeSearchResultSet.class)
                        .subscribeOn(Schedulers.io())
                        .timeout(1, TimeUnit.SECONDS)
                        .onErrorComplete())
                .toList()
                .blockingGet();
    }

    @CheckReturnValue
    public List<EdgeDomainSearchResults> queryDomains(Context ctx, List<EdgeDomainSearchSpecification> specs) {
        return Observable.fromArray(specs)
                .concatMap(s -> postGet(ctx, "/search-domain/", s, EdgeDomainSearchResults.class)
                        .subscribeOn(Schedulers.io())
                        .timeout(1, TimeUnit.SECONDS)
                        .onErrorComplete())
                .toList()
                .blockingGet();
    }


    @CheckReturnValue
    public Observable<Boolean> isBlocked(Context ctx) {
        return super.get(ctx, "/is-blocked", Boolean.class);
    }

}

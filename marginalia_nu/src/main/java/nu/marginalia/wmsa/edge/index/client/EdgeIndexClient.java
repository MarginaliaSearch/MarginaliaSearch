package nu.marginalia.wmsa.edge.index.client;

import com.google.inject.Singleton;
import io.prometheus.client.Summary;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentsMetadata;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeId;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultItem;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultSet;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSpecification;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchResults;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchSpecification;
import nu.wmsa.wmsa.edge.index.proto.IndexPutKeywordsReq;

import javax.annotation.CheckReturnValue;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class EdgeIndexClient extends AbstractDynamicClient implements EdgeIndexWriterClient {

    private static final Summary wmsa_search_index_api_time = Summary.build().name("wmsa_search_index_api_time").help("-").register();

    public EdgeIndexClient() {
        super(ServiceDescriptor.EDGE_INDEX);
        setTimeout(30);
    }

    @Override
    public void putWords(Context ctx, EdgeId<EdgeDomain> domain, EdgeId<EdgeUrl> url,
                         EdgePageDocumentsMetadata metadata,
                         DocumentKeywords wordSet, int writer
    )
    {

        var keywordBuilder =
                IndexPutKeywordsReq.newBuilder()
                    .setDomain(domain.id())
                    .setUrl(url.id())
                    .setMetadata(metadata.encode())
                    .setIndex(writer);

        var wordSetBuilder = IndexPutKeywordsReq.WordSet.newBuilder();
        wordSetBuilder.addAllWords(List.of(wordSet.keywords()));
        for (var meta : wordSet.metadata()) {
            wordSetBuilder.addMeta(meta);
        }
        keywordBuilder.addWordSet(wordSetBuilder.build());

        var req = keywordBuilder.build();

        this.post(ctx, "/words/", req).blockingSubscribe();
    }


    @CheckReturnValue
    public List<EdgeSearchResultItem> query(Context ctx, EdgeSearchSpecification specs) {
        return wmsa_search_index_api_time.time(
                () -> this.postGet(ctx, "/search/", specs, EdgeSearchResultSet.class).blockingFirst().getResults()
        );
    }

    @CheckReturnValue
    public List<EdgeDomainSearchResults> queryDomains(Context ctx, List<EdgeDomainSearchSpecification> specs) {
        return Observable.fromIterable(specs)
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

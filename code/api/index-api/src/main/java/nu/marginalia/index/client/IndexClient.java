package nu.marginalia.index.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.prometheus.client.Summary;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.Context;
import nu.marginalia.client.exception.RouteNotConfiguredException;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;

import java.util.List;

import javax.annotation.CheckReturnValue;
import java.util.ServiceConfigurationError;
import java.util.UUID;

@Singleton
public class IndexClient extends AbstractDynamicClient {

    private static final Summary wmsa_search_index_api_time = Summary.build().name("wmsa_search_index_api_time").help("-").register();
    private final MessageQueueFactory messageQueueFactory;

    MqOutbox outbox;

    @Inject
    public IndexClient(ServiceDescriptors descriptors,
                       MessageQueueFactory messageQueueFactory,
                       @Named("wmsa-system-node") Integer nodeId)
    {
        super(descriptors.forId(ServiceId.Index), GsonFactory::get);
        this.messageQueueFactory = messageQueueFactory;

        String inboxName = ServiceId.Index.name;
        String outboxName = "pp:"+System.getProperty("service-name", UUID.randomUUID().toString());
        outbox = messageQueueFactory.createOutbox(inboxName, nodeId, outboxName, nodeId, UUID.randomUUID());
        setTimeout(30);
    }

    public MqOutbox outbox() {
        return outbox;
    }

    @CheckReturnValue
    public SearchResultSet query(Context ctx, int node, SearchSpecification specs) {
        return wmsa_search_index_api_time.time(
                () -> this.postGet(ctx, node,"/search/", specs, SearchResultSet.class).blockingFirst()
        );
    }

    @CheckReturnValue
    public SearchResultSet query(Context ctx, List<Integer> nodes, SearchSpecification specs) {
        return Observable.fromIterable(nodes)
                .flatMap(node -> {
                    try {
                        return this
                                .postGet(ctx, node, "/search/", specs, SearchResultSet.class).onErrorReturn(t -> new SearchResultSet())
                                .observeOn(Schedulers.io());
                    } catch (RouteNotConfiguredException ex) {
                        return Observable.empty();
                    }
                })
                .reduce(SearchResultSet::combine)
                .blockingGet();
    }


    @CheckReturnValue
    public Observable<Boolean> isBlocked(Context ctx, int node) {
        return super.get(ctx, node, "/is-blocked", Boolean.class);
    }

    public long triggerRepartition(int node) throws Exception {
        return messageQueueFactory.sendSingleShotRequest(
                ServiceId.Index.withNode(node),
                IndexMqEndpoints.INDEX_REPARTITION,
                null
        );
    }

    public long triggerRerank(int node) throws Exception {
        return messageQueueFactory.sendSingleShotRequest(
                ServiceId.Index.withNode(node),
                IndexMqEndpoints.INDEX_RERANK,
                null
        );
    }
}

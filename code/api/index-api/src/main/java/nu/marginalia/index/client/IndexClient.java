package nu.marginalia.index.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.prometheus.client.Summary;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.WmsaHome;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.Context;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;

import javax.annotation.CheckReturnValue;
import java.util.UUID;

@Singleton
public class IndexClient extends AbstractDynamicClient {

    private static final Summary wmsa_search_index_api_time = Summary.build().name("wmsa_search_index_api_time").help("-").register();

    private final MqOutbox outbox;

    @Inject
    public IndexClient(ServiceDescriptors descriptors,
                       MessageQueueFactory messageQueueFactory) {
        super(descriptors.forId(ServiceId.Index), WmsaHome.getHostsFile(), GsonFactory::get);

        String inboxName = ServiceId.Index.name + ":" + "0";
        String outboxName = System.getProperty("service-name", UUID.randomUUID().toString());

        outbox = messageQueueFactory.createOutbox(inboxName, outboxName, UUID.randomUUID());

        setTimeout(30);
    }


    public MqOutbox outbox() {
        return outbox;
    }

    @CheckReturnValue
    public SearchResultSet query(Context ctx, SearchSpecification specs) {
        return wmsa_search_index_api_time.time(
                () -> this.postGet(ctx, "/search/", specs, SearchResultSet.class).blockingFirst()
        );
    }


    @CheckReturnValue
    public Observable<Boolean> isBlocked(Context ctx) {
        return super.get(ctx, "/is-blocked", Boolean.class);
    }

}

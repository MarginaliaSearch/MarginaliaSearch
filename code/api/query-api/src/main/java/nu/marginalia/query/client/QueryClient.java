package nu.marginalia.query.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.prometheus.client.Summary;
import nu.marginalia.WmsaHome;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.Context;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.query.model.QueryParams;
import nu.marginalia.query.model.QueryResponse;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.util.UUID;

@Singleton
public class QueryClient extends AbstractDynamicClient {

    private static final Summary wmsa_search_index_api_delegate_time = Summary.build().name("wmsa_search_index_api_delegate_time").help("-").register();
    private static final Summary wmsa_search_index_api_search_time = Summary.build().name("wmsa_search_index_api_search_time").help("-").register();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public QueryClient(ServiceDescriptors descriptors) {

        super(descriptors.forId(ServiceId.Query), GsonFactory::get);
    }

    /** Delegate an Index API style query directly to the index service */
    @CheckReturnValue
    public SearchResultSet delegate(Context ctx, SearchSpecification specs) {
        return wmsa_search_index_api_delegate_time.time(
                () -> this.postGet(ctx, 0, "/delegate/", specs, SearchResultSet.class).blockingFirst()
        );
    }
    @CheckReturnValue
    public QueryResponse search(Context ctx, QueryParams params) {
        return wmsa_search_index_api_search_time.time(
                () -> this.postGet(ctx, 0, "/search/", params, QueryResponse.class).blockingFirst()
        );
    }

}

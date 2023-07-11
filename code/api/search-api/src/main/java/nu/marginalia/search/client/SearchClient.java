package nu.marginalia.search.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.search.client.model.ApiSearchResults;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.WmsaHome;
import nu.marginalia.client.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Singleton
public class SearchClient extends AbstractDynamicClient {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MqOutbox outbox;

    @Inject
    public SearchClient(ServiceDescriptors descriptors,
                        MqPersistence persistence) {

        super(descriptors.forId(ServiceId.Search), WmsaHome.getHostsFile(), GsonFactory::get);

        String inboxName = ServiceId.Search.name + ":" + "0";
        String outboxName = System.getProperty("service-name", UUID.randomUUID().toString());

        outbox = new MqOutbox(persistence, inboxName, outboxName, UUID.randomUUID());

    }


    public MqOutbox outbox() {
        return outbox;
    }

    @CheckReturnValue
    public Observable<ApiSearchResults> query(Context ctx, String queryString, int count, int profile) {
        return this.get(ctx, String.format("/api/search?query=%s&count=%d&index=%d", URLEncoder.encode(queryString, StandardCharsets.UTF_8), count, profile), ApiSearchResults.class);
    }

}

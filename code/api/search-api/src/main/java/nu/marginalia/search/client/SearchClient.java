package nu.marginalia.search.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.model.gson.GsonFactory;
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

@Singleton
public class SearchClient extends AbstractDynamicClient {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchClient(ServiceDescriptors descriptors) {
        super(descriptors.forId(ServiceId.Search), WmsaHome.getHostsFile(), GsonFactory::get);
    }

    @CheckReturnValue
    public Observable<ApiSearchResults> query(Context ctx, String queryString, int count, int profile) {
        return this.get(ctx, String.format("/api/search?query=%s&count=%d&index=%d", URLEncoder.encode(queryString, StandardCharsets.UTF_8), count, profile), ApiSearchResults.class);
    }

}

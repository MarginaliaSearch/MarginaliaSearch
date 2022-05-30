package nu.marginalia.wmsa.edge.search.client;

import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.wmsa.api.model.ApiSearchResults;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Singleton
public class EdgeSearchClient extends AbstractDynamicClient {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public EdgeSearchClient() {
        super(ServiceDescriptor.EDGE_SEARCH);
    }

    @CheckReturnValue
    public Observable<ApiSearchResults> query(Context ctx, String queryString, int count, int profile) {
        return this.get(ctx, String.format("/api/search?query=%s&count=%d&index=%d", URLEncoder.encode(queryString, StandardCharsets.UTF_8), count, profile), ApiSearchResults.class);
    }

}

package nu.marginalia.wmsa.memex.client;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;


public class MemexApiClient extends AbstractDynamicClient {
    @Inject
    public MemexApiClient() {
        super(ServiceDescriptor.EDGE_MEMEX);
    }

}

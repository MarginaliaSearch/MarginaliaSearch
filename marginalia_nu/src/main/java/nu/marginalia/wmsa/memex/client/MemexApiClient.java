package nu.marginalia.wmsa.memex.client;

import com.google.inject.Inject;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;


public class MemexApiClient extends AbstractDynamicClient {
    @Inject
    public MemexApiClient() {
        super(ServiceDescriptor.EDGE_MEMEX);
    }

}

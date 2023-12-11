package nu.marginalia.client;

import com.google.gson.Gson;
import nu.marginalia.service.descriptor.ServiceDescriptor;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class AbstractDynamicClient extends AbstractClient {
    private final ServiceDescriptor service;
    private final AbortingScheduler scheduler;

    public AbstractDynamicClient(@Nonnull ServiceDescriptor service, Supplier<Gson> gsonProvider) {
        super(
                service,
                10000,
                gsonProvider
        );

        this.service = service;
        this.scheduler = new AbortingScheduler();
    }

    @Override
    public String name() {
        return service.name;
    }

    public ServiceDescriptor getService() {
        return service;
    }

    @Override
    public AbortingScheduler scheduler() {
        return scheduler;
    }

}

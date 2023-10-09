package nu.marginalia.client;

import com.google.gson.Gson;
import io.reactivex.rxjava3.core.Observable;
import lombok.SneakyThrows;
import nu.marginalia.client.model.ClientRoute;
import nu.marginalia.service.descriptor.ServiceDescriptor;
import nu.marginalia.service.descriptor.HostsFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class AbstractDynamicClient extends AbstractClient {
    private final ServiceDescriptor service;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AbortingScheduler scheduler;

    public AbstractDynamicClient(@Nonnull ServiceDescriptor service, HostsFile hosts, Supplier<Gson> gsonProvider) {
        super(
                new ClientRoute(hosts.getHost(service), service.port),
                10,
                gsonProvider
        );

        this.service = service;
        this.scheduler = new AbortingScheduler(name());
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

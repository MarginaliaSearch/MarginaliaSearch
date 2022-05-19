package nu.marginalia.wmsa.client;

import io.reactivex.rxjava3.core.Observable;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class AbstractDynamicClient extends AbstractClient {
    private final ServiceDescriptor service;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AbortingScheduler scheduler;

    public AbstractDynamicClient(@Nonnull ServiceDescriptor service) {
        super("localhost",  service.port, 10);

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

    @SneakyThrows
    public void blockingWait() {
        logger.info("Waiting for route to {}", service);
        while (!isAlive()) {
            Thread.sleep(1000);
        }
    }

    @Override
    public AbortingScheduler scheduler() {
        return scheduler;
    }

    public Observable<String> who(Context ctx) {
        return get(ctx, "/public/who");
    }
    public Observable<String> ping(Context ctx) {
        return get(ctx, "/internal/ping");
    }
}

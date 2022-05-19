package nu.marginalia.wmsa.configuration.module;

import com.google.inject.name.Named;
import io.reactivex.rxjava3.core.Flowable;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import org.apache.http.HttpResponse;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class PortProvider implements Provider<Integer> {
    private static final Integer DEFAULT_PORT = 5000;
    private final int monitorPort;
    private final String monitorHost;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private final int timeout = 10;
    @Inject
    public PortProvider(@Named("monitor-port") Integer monitorPort,
                        @Named("monitor-host") String monitorHost,
                        @Named("monitor-boot-timeout") Integer timeout) {
        this.monitorHost = monitorHost;
        this.monitorPort = monitorPort;
    }

    @Override
    public Integer get() {
        return ServiceDescriptor.byName(System.getProperty("service-name")).port;
    }

    private Publisher<?> repeatDelay(Flowable<Throwable> error) {
        return error.delay(1, TimeUnit.SECONDS);
    }

    private String accept200(HttpResponse rsp) throws IOException {
        if (rsp.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Monitor responded unexpected status "
                    + rsp.getStatusLine().getStatusCode());
        }
        return new String(rsp.getEntity().getContent().readAllBytes());
    }
}

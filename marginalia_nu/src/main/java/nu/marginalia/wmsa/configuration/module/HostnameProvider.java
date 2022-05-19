package nu.marginalia.wmsa.configuration.module;

import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

public class HostnameProvider implements Provider<String> {
    private static final String DEFAULT_HOSTNAME = "127.0.0.1";
    private final int monitorPort;
    private final String monitorHost;
    private final int timeout;
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public HostnameProvider(@Named("monitor-port") Integer monitorPort,
                            @Named("monitor-host") String monitorHost,
                            @Named("monitor-boot-timeout") Integer timeout
                            ) {
        this.monitorHost = monitorHost;
        this.monitorPort = monitorPort;
        this.timeout = timeout;
    }

    @Override
    public String get() {
        var override = System.getProperty("service-host");
        if (null != override) {
            return override;
        }
        return DEFAULT_HOSTNAME;
    }

}

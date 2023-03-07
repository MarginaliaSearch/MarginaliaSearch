package nu.marginalia.service.module;

import com.google.inject.name.Named;

import javax.inject.Inject;
import javax.inject.Provider;

public class MetricsPortProvider implements Provider<Integer> {
    private final Integer servicePort;

    @Inject
    public MetricsPortProvider(@Named("service-port") Integer servicePort) {
        this.servicePort = servicePort;
    }

    @Override
    public Integer get() {
        return servicePort+1000;
    }

}

package nu.marginalia.wmsa.configuration.module;

import com.google.inject.name.Named;

import javax.inject.Inject;

public class LoggerConfiguration {
    @Inject
    public LoggerConfiguration(@Named("service-name") String serviceName) {
        System.setProperty("service-name", serviceName);
    }

}

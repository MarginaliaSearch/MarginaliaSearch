package nu.marginalia.service.module;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.service.ServiceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

public class ServiceConfigurationModule extends AbstractModule {
    private final ServiceId id;
    private final String[] cmdArgs;
    private static final Logger logger = LoggerFactory.getLogger(ServiceConfigurationModule.class);

    public ServiceConfigurationModule(ServiceId id, String[] cmdArgs) {
        this.id = id;
        this.cmdArgs = cmdArgs;
    }

    public void configure() {
        int node = getNode();

        var configObject = new ServiceConfiguration(cmdArgs,
                id,
                node,
                getBindAddress(),
                getExternalHost(),
                getPrometheusPort(),
                UUID.randomUUID()
        );

        logger.info("Service configuration: {}", configObject);

        bind(Integer.class).annotatedWith(Names.named("wmsa-system-node")).toInstance(node);
        bind(ServiceConfiguration.class).toInstance(configObject);
    }

    private int getPrometheusPort() {
        String prometheusPortEnv = System.getenv("WMSA_PROMETHEUS_PORT");
        if (prometheusPortEnv != null) {
            return Integer.parseInt(prometheusPortEnv);
        }

        Integer prometheusPortProperty = Integer.getInteger("service.prometheus-port");
        if (prometheusPortProperty != null) {
            return prometheusPortProperty;
        }

        return 7000;
    }

    private int getNode() {
        String nodeEnv = Objects.requireNonNullElse(System.getenv("WMSA_SERVICE_NODE"),
                System.getProperty("system.serviceNode", "0"));

        return Integer.parseInt(nodeEnv);
    }

    /** Get the external host for the service. This is announced via the service registry,
     * and should be an IP address or hostname that resolves to this machine */
    private String getExternalHost() {
        // Check for an environment variable override
        String configuredValue;
        if (null != (configuredValue = System.getenv("SERVICE_HOST"))) {
            return configuredValue;
        }

        // Check for a system property override
        if (null != (configuredValue = System.getProperty("service.host"))) {
            return configuredValue;
        }

        // If we're in docker, we'll use the hostname
        if (Boolean.getBoolean("service.useDockerHostname")) {
            return System.getenv("HOSTNAME");
        }

        // If we've not been told about a host, and we're not in docker, we'll fall back to localhost
        // and hope the operator's remembered to enable random port assignment via zookeeper
        return "127.0.0.1";
    }

    /** Get the bind address for the service. This is the address that the service will listen on.
     */
    private String getBindAddress() {
        String configuredValue = System.getProperty("service.bind-address");
        if (configuredValue != null) {
            return configuredValue;
        }

        return "127.0.0.1";
    }

}

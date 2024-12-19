package nu.marginalia.service.module;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.service.ServiceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Objects;
import java.util.UUID;

public class ServiceConfigurationModule extends AbstractModule {
    private final ServiceId id;
    private static final Logger logger = LoggerFactory.getLogger(ServiceConfigurationModule.class);

    public ServiceConfigurationModule(ServiceId id) {
        this.id = id;
    }

    public void configure() {
        int node = getNode();

        var configObject = new ServiceConfiguration(id,
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

        if (Boolean.getBoolean("system.multiFace")) {
            try {
                String localNetworkIp = getLocalNetworkIP();
                if (null != localNetworkIp) {
                    return localNetworkIp;
                }
            }
            catch (Exception ex) {
                logger.warn("Failed to get local network IP", ex);
            }
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
            logger.info("Using configured bind address {}", configuredValue);
            return configuredValue;
        }

        if (Boolean.getBoolean("system.multiFace")) {
            try {
                return Objects.requireNonNullElse(getLocalNetworkIP(), "0.0.0.0");
            } catch (Exception ex) {
                logger.warn("Failed to get local network IP, falling back to bind to 0.0.0.0", ex);
                return "0.0.0.0";
            }
        }
        else {
            return "0.0.0.0";
        }
    }

    public static String getLocalNetworkIP() throws Exception {
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();

        while (nets.hasMoreElements()) {
            NetworkInterface netif = nets.nextElement();
            if (!netif.isUp() || netif.isLoopback()) {
                continue;
            }

            Enumeration<InetAddress> inetAddresses = netif.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress addr = inetAddresses.nextElement();
                if (addr.isSiteLocalAddress() && !addr.isLoopbackAddress()) {
                    return addr.getHostAddress();
                }
            }
        }
        return null;
    }

}

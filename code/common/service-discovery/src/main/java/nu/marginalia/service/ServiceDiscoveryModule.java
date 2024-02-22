package nu.marginalia.service;

import com.google.inject.AbstractModule;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.ZkServiceRegistry;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/** Provides a Guice module for service discovery. */
public class ServiceDiscoveryModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscoveryModule.class);

    public void configure() {
        var hosts = getZookeeperHosts().orElseThrow(() -> new IllegalStateException("Zookeeper hosts not set"));
        logger.info("Using Zookeeper service registry at {}", hosts);
        CuratorFramework client = CuratorFrameworkFactory
                .newClient(hosts, new ExponentialBackoffRetry(100, 10, 1000));

        bind(CuratorFramework.class).toInstance(client);
        bind(ServiceRegistryIf.class).to(ZkServiceRegistry.class);
    }

    private Optional<String> getZookeeperHosts() {
        if (System.getProperty("zookeeper-hosts") != null) {
            return Optional.of(System.getProperty("zookeeper-hosts"));
        }
        return Optional.ofNullable(System.getenv("ZOOKEEPER_HOSTS"));
    }

}

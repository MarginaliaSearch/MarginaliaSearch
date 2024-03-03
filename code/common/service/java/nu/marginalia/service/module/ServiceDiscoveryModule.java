package nu.marginalia.service.module;

import com.google.inject.AbstractModule;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.ZkServiceRegistry;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides a Guice module for service discovery. */
public class ServiceDiscoveryModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscoveryModule.class);

    public void configure() {
        var hosts = getZookeeperHosts();
        logger.info("Using Zookeeper service registry at {}", hosts);
        CuratorFramework client = CuratorFrameworkFactory
                .newClient(hosts, new ExponentialBackoffRetry(100, 10, 1000));

        bind(CuratorFramework.class).toInstance(client);
        bind(ServiceRegistryIf.class).to(ZkServiceRegistry.class);
    }

    private String getZookeeperHosts() {
        if (System.getProperty("zookeeper-hosts") != null) {
            return System.getProperty("zookeeper-hosts");
        }
        String env = System.getenv("ZOOKEEPER_HOSTS");
        if (null == env) {
            System.err.println("""
                ZOOKEEPER_HOSTS not set.  This probably means that you are running an old installation,
                   or that the environment is not set up correctly.  
                   
                See the 2024-03+ migration notes, https://docs.marginalia.nu/6_notes/6_1__migrate_2024_03_plus
                
                """);
        }
        return env;
    }

}

package nu.marginalia.coordination;

import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DomainCoordinationModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(DomainCoordinationModule.class);

    public DomainCoordinationModule() {
    }

    public void configure() {
        if (Boolean.getBoolean("system.zookeeperDomainCoordination")) {
            bind(DomainCoordinator.class).to(ZookeeperDomainCoordinator.class);
        }
        else {
            bind(DomainCoordinator.class).to(LocalDomainCoordinator.class);
        }
    }
}

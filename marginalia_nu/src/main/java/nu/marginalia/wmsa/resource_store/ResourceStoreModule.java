package nu.marginalia.wmsa.resource_store;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.wmsa.configuration.WmsaHome;

import java.nio.file.Path;

public class ResourceStoreModule extends AbstractModule {
    public void configure() {
        bind(Path.class).annotatedWith(Names.named("data-path")).toInstance(WmsaHome.getDisk("resource-store"));
    }


}

package nu.marginalia.wmsa.resource_store;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import java.nio.file.Path;

public class ResourceStoreModule extends AbstractModule {
    public void configure() {
        bind(String.class).annotatedWith(Names.named("external-url")).toInstance("https://reddit.marginalia.nu/");
        bind(Path.class).annotatedWith(Names.named("data-path")).toInstance(Path.of("/var/lib/wmsa/archive.fast/resources"));
    }


}

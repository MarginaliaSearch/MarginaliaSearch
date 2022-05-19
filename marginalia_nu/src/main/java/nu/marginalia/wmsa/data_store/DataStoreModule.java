package nu.marginalia.wmsa.data_store;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class DataStoreModule extends AbstractModule {

    public void configure() {
        bind(String.class).annotatedWith(Names.named("file-storage-dir")).toInstance("/tmp/files");
        bind(String.class).annotatedWith(Names.named("distro-file-name")).toInstance("wmsa.jar");
        bind(String.class).annotatedWith(Names.named("file-tmp-dir")).toInstance("/tmp");
    }

}

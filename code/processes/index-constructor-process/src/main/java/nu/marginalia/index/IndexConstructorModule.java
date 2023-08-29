package nu.marginalia.index;

import com.google.inject.AbstractModule;
import nu.marginalia.ProcessConfiguration;

import java.util.UUID;

public class IndexConstructorModule extends AbstractModule {
    @Override
    public void configure() {
        bind(ProcessConfiguration.class).toInstance(new ProcessConfiguration("index-constructor", 0, UUID.randomUUID()));

    }
}

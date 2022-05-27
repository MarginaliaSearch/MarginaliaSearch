package nu.marginalia.wmsa.encyclopedia;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.WmsaHome;

import java.nio.file.Path;

public class EncyclopediaModule extends AbstractModule {
    @SneakyThrows
    @Override
    public void configure() {
        bind(Path.class)
                .annotatedWith(Names.named("wiki-path"))
                .toInstance(WmsaHome.getDisk("encyclopedia"));
    }
}

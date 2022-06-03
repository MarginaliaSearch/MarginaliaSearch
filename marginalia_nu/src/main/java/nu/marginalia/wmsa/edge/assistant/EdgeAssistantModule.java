package nu.marginalia.wmsa.edge.assistant;

import com.google.inject.AbstractModule;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.wmsa.configuration.WmsaHome;

import java.nio.file.Path;

import static com.google.inject.name.Names.named;

public class EdgeAssistantModule extends AbstractModule {
    public void configure() {
        bind(Path.class).annotatedWith(named("suggestions-file")).toInstance(WmsaHome.getHomePath().resolve("suggestions.txt"));

        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
    }
}

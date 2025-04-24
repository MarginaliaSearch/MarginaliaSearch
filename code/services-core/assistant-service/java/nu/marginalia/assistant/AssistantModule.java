package nu.marginalia.assistant;

import com.google.inject.AbstractModule;
import nu.marginalia.LanguageModels;
import nu.marginalia.WmsaHome;

import java.nio.file.Path;

import static com.google.inject.name.Names.named;

public class AssistantModule extends AbstractModule {
    public void configure() {
        bind(Path.class).annotatedWith(named("suggestions-file1")).toInstance(WmsaHome.getHomePath().resolve("data/suggestions2.txt.gz"));
        bind(Path.class).annotatedWith(named("suggestions-file2")).toInstance(WmsaHome.getHomePath().resolve("data/suggestions3.txt.gz"));

        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
    }
}

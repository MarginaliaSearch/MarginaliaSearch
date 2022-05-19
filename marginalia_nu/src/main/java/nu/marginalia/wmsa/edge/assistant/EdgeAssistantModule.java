package nu.marginalia.wmsa.edge.assistant;

import com.google.inject.AbstractModule;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;

import java.nio.file.Path;

import static com.google.inject.name.Names.named;

public class EdgeAssistantModule extends AbstractModule {
    public void configure() {
        bind(Path.class).annotatedWith(named("suggestions-file")).toInstance(Path.of("/var/lib/wmsa/suggestions.txt"));
        bind(LanguageModels.class).toInstance(new LanguageModels(
                Path.of("/var/lib/wmsa/model/ngrams-generous-emstr.bin"),
                Path.of("/var/lib/wmsa/model/tfreq-new-algo3.bin"),
                Path.of("/var/lib/wmsa/model/opennlp-sentence.bin"),
                Path.of("/var/lib/wmsa/model/English.RDR"),
                Path.of("/var/lib/wmsa/model/English.DICT"),
                Path.of("/var/lib/wmsa/model/opennlp-tok.bin")
        ));
    }
}

package nu.marginalia.wmsa.edge.search;

import com.google.inject.AbstractModule;
import nu.marginalia.util.language.conf.LanguageModels;

import java.nio.file.Path;

public class EdgeSearchModule extends AbstractModule {

    public void configure() {

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

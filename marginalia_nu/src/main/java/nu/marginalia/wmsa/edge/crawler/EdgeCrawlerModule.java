package nu.marginalia.wmsa.edge.crawler;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import opennlp.tools.langdetect.LanguageDetector;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

public class EdgeCrawlerModule extends AbstractModule {


    public void configure() {
        bind(String.class).annotatedWith(Names.named("user-agent")).toInstance("search.marginalia.nu");
        bind(String.class).annotatedWith(Names.named("user-agent-robots")).toInstance("search.marginalia.nu");
        bind(Path.class).annotatedWith(Names.named("crawl-specifications-path")).toInstance(Path.of("/var/lib/wmsa/crawler-specs.dat"));

        bind(LanguageModels.class).toInstance(new LanguageModels(
                Path.of("/var/lib/wmsa/model/ngrams-generous-emstr.bin"),
                Path.of("/var/lib/wmsa/model/tfreq-new-algo3.bin"),
                Path.of("/var/lib/wmsa/model/opennlp-sentence.bin"),
                Path.of("/var/lib/wmsa/model/English.RDR"),
                Path.of("/var/lib/wmsa/model/English.DICT"),
                Path.of("/var/lib/wmsa/model/opennlp-tok.bin")
        ));
    }

    @Provides @SneakyThrows
    LanguageDetector detector() {
        final Path[] paths = new Path[]{
                Path.of("/app/resources/langdetect-183.bin"),
                Path.of("/home/vlofgren/Code/wmsa-b/src/main/nlp-models/langdetect-183.bin")
        };
        Optional<Path> path = Arrays.stream(paths).filter(p->p.toFile().exists()).findAny();
        if (path.isEmpty()) {
            throw new FileNotFoundException("Could not find langdetect-183.bin");
        }

        try (var is = Files.newInputStream(path.get())) {
            return new LanguageDetectorME(new LanguageDetectorModel(is));
        }
    }

}

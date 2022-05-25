package nu.marginalia.wmsa.edge.converting;

import com.google.gson.*;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import marcono1234.gson.recordadapter.RecordTypeAdapterFactory;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.net.URISyntaxException;
import java.nio.file.Path;

public class ConverterModule extends AbstractModule {

    private final EdgeCrawlPlan plan;

    public ConverterModule(EdgeCrawlPlan plan) {
        this.plan = plan;
    }

    public void configure() {
        bind(EdgeCrawlPlan.class).toInstance(plan);

        bind(Gson.class).toInstance(createGson());

        bind(Double.class).annotatedWith(Names.named("min-document-quality")).toInstance(-15.);
        bind(Integer.class).annotatedWith(Names.named("min-document-length")).toInstance(100);
        bind(Integer.class).annotatedWith(Names.named("max-title-length")).toInstance(128);
        bind(Integer.class).annotatedWith(Names.named("max-summary-length")).toInstance(255);

        bind(LanguageModels.class).toInstance(new LanguageModels(
                Path.of("/var/lib/wmsa/model/ngrams-generous-emstr.bin"),
                Path.of("/var/lib/wmsa/model/tfreq-new-algo3.bin"),
                Path.of("/var/lib/wmsa/model/opennlp-sentence.bin"),
                Path.of("/var/lib/wmsa/model/English.RDR"),
                Path.of("/var/lib/wmsa/model/English.DICT"),
                Path.of("/var/lib/wmsa/model/opennlp-tok.bin")
        ));
    }

    private Gson createGson() {

        return new GsonBuilder()
                .registerTypeAdapter(EdgeUrl.class, (JsonSerializer<EdgeUrl>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
                .registerTypeAdapter(EdgeDomain.class, (JsonSerializer<EdgeDomain>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
                .registerTypeAdapter(EdgeUrl.class, (JsonDeserializer<EdgeUrl>) (json, typeOfT, context) -> {
                    try {
                        return new EdgeUrl(json.getAsString());
                    } catch (URISyntaxException e) {
                        throw new JsonParseException("URL Parse Exception", e);
                    }
                })
                .registerTypeAdapter(EdgeDomain.class, (JsonDeserializer<EdgeDomain>) (json, typeOfT, context) -> new EdgeDomain(json.getAsString()))
                .registerTypeAdapterFactory(RecordTypeAdapterFactory.builder().allowMissingComponentValues().create())
                .create();
    }

}

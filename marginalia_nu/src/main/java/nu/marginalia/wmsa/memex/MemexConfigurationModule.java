package nu.marginalia.wmsa.memex;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import nu.marginalia.wmsa.memex.system.MemexFileWriter;

import java.nio.file.Path;

public class MemexConfigurationModule extends AbstractModule {
    public void configure() {
        bind(Path.class).annotatedWith(Names.named("memex-root")).toInstance(Path.of("/var/lib/wmsa/memex"));
        bind(Path.class).annotatedWith(Names.named("memex-html-resources")).toInstance(Path.of("/var/lib/wmsa/memex-html"));
        bind(Path.class).annotatedWith(Names.named("memex-gmi-resources")).toInstance(Path.of("/var/lib/wmsa/memex-gmi"));
        bind(String.class).annotatedWith(Names.named("tombestone-special-file")).toInstance("/special/tombstone.gmi");
        bind(String.class).annotatedWith(Names.named("redirects-special-file")).toInstance("/special/redirect.gmi");

        bind(MemexFileWriter.class).annotatedWith(Names.named("html")).toProvider(MemexHtmlWriterProvider.class);
        bind(MemexFileWriter.class).annotatedWith(Names.named("gmi")).toProvider(MemexGmiWriterProvider.class);
    }



    public static class MemexHtmlWriterProvider implements Provider<MemexFileWriter> {
        private final Path path;

        @Inject
        public MemexHtmlWriterProvider(@Named("memex-html-resources") Path resources) {
            this.path = resources;
        }
        @Override
        public MemexFileWriter get() {
            return new MemexFileWriter(path);
        }
    }

    public static class MemexGmiWriterProvider implements Provider<MemexFileWriter> {
        private final Path path;

        @Inject
        public MemexGmiWriterProvider(@Named("memex-gmi-resources") Path resources) {
            this.path = resources;
        }
        @Override
        public MemexFileWriter get() {
            return new MemexFileWriter(path);
        }
    }
}

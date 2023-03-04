package nu.marginalia.memex.memex;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import lombok.SneakyThrows;
import nu.marginalia.memex.gemini.GeminiService;
import nu.marginalia.memex.gemini.GeminiServiceDummy;
import nu.marginalia.memex.gemini.GeminiServiceImpl;
import nu.marginalia.memex.memex.system.MemexFileWriter;
import nu.marginalia.memex.memex.system.git.MemexGitRepo;
import nu.marginalia.memex.memex.system.git.MemexGitRepoDummy;
import nu.marginalia.memex.memex.system.git.MemexGitRepoImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class MemexConfigurationModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(MemexConfigurationModule.class);

    private static final String MEMEX_ROOT_PROPERTY = System.getProperty("memex-root", "/var/lib/wmsa/memex");
    private static final String MEMEX_HTML_PROPERTY = System.getProperty("memex-html-resources", "/var/lib/wmsa/memex-html");
    private static final String MEMEX_GMI_PROPERTY = System.getProperty("memex-gmi-resources", "/var/lib/wmsa/memex-gmi");

    private static final boolean MEMEX_DISABLE_GIT    = Boolean.getBoolean("memex-disable-git");
    private static final boolean MEMEX_DISABLE_GEMINI = Boolean.getBoolean("memex-disable-gemini");

    @SneakyThrows
    public MemexConfigurationModule() {
        Thread.sleep(100);
    }

    public void configure() {
        bind(Path.class).annotatedWith(Names.named("memex-root")).toInstance(Path.of(MEMEX_ROOT_PROPERTY));
        bind(Path.class).annotatedWith(Names.named("memex-html-resources")).toInstance(Path.of(MEMEX_HTML_PROPERTY));
        bind(Path.class).annotatedWith(Names.named("memex-gmi-resources")).toInstance(Path.of(MEMEX_GMI_PROPERTY));

        bind(String.class).annotatedWith(Names.named("tombestone-special-file")).toInstance("/special/tombstone.gmi");
        bind(String.class).annotatedWith(Names.named("redirects-special-file")).toInstance("/special/redirect.gmi");

        switchImpl(MemexGitRepo.class, MEMEX_DISABLE_GIT, MemexGitRepoDummy.class, MemexGitRepoImpl.class);
        switchImpl(GeminiService.class, MEMEX_DISABLE_GEMINI, GeminiServiceDummy.class, GeminiServiceImpl.class);

        bind(MemexFileWriter.class).annotatedWith(Names.named("html")).toProvider(MemexHtmlWriterProvider.class);
        bind(MemexFileWriter.class).annotatedWith(Names.named("gmi")).toProvider(MemexGmiWriterProvider.class);
    }

    <T> void switchImpl(Class<T> impl, boolean param, Class<? extends T> ifEnabled, Class<? extends T> ifDisabled) {
        final Class<? extends T> choice;
        if (param) {
            choice = ifEnabled;
        }
        else {
            choice = ifDisabled;
        }
        bind(impl).to(choice).asEagerSingleton();
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

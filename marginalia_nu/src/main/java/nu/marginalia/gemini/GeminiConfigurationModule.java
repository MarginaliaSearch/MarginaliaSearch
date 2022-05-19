package nu.marginalia.gemini;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import nu.marginalia.wmsa.memex.system.MemexFileWriter;

import java.nio.file.Path;

public class GeminiConfigurationModule extends AbstractModule {
    public void configure() {
        bind(Path.class).annotatedWith(Names.named("gemini-server-root")).toInstance(Path.of("/var/lib/wmsa/memex-gmi"));
        bind(Path.class).annotatedWith(Names.named("gemini-cert-file")).toInstance(Path.of("/var/lib/wmsa/gemini/crypto.jks"));
        bind(Path.class).annotatedWith(Names.named("gemini-cert-password-file")).toInstance(Path.of("/var/lib/wmsa/gemini/password.dat"));
        bind(Integer.class).annotatedWith(Names.named("gemini-server-port")).toInstance(1965);

    }

}

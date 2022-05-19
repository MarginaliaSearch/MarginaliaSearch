package nu.marginalia.wmsa.auth;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import java.nio.file.Path;

public class AuthConfigurationModule extends AbstractModule {
    public void configure() {
        bind(Path.class).annotatedWith(Names.named("password-file")).toInstance(Path.of("/var/lib/wmsa/password.dat"));
    }
}

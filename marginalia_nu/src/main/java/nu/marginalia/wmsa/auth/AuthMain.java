package nu.marginalia.wmsa.auth;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

import java.io.IOException;

public class AuthMain extends MainClass {

    @Inject
    public AuthMain(AuthService service) throws IOException {
    }

    public static void main(String... args) {
        init(ServiceDescriptor.AUTH, args);

        Injector injector = Guice.createInjector(
                new AuthConfigurationModule(),
                new ConfigurationModule());
        injector.getInstance(AuthMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}

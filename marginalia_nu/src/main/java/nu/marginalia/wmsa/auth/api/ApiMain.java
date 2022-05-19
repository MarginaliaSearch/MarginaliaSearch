package nu.marginalia.wmsa.auth.api;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

import java.io.IOException;

public class ApiMain extends MainClass {

    @Inject
    public ApiMain(ApiService service) {
    }

    public static void main(String... args) {
        init(ServiceDescriptor.API, args);

        Injector injector = Guice.createInjector(
                new DatabaseModule(),
                new ConfigurationModule());
        injector.getInstance(ApiMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}

package nu.marginalia.wmsa.edge.dating;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;
import spark.Spark;

import java.io.IOException;

public class DatingMain extends MainClass {
    final DatingService service;

    @Inject
    public DatingMain(DatingService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.DATING, args);

        Spark.staticFileLocation("/static/dating/");

        Injector injector = Guice.createInjector(
                new DatingModule(),
                new ConfigurationModule(),
                new DatabaseModule()
        );

        injector.getInstance(DatingMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}

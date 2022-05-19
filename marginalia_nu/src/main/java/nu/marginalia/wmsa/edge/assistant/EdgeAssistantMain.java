package nu.marginalia.wmsa.edge.assistant;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

public class EdgeAssistantMain extends MainClass {
    private final EdgeAssistantService service;

    @Inject
    public EdgeAssistantMain(EdgeAssistantService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.EDGE_ASSISTANT, args);

        Injector injector = Guice.createInjector(
                new EdgeAssistantModule(),
                new ConfigurationModule(),
                new DatabaseModule()
        );

        injector.getInstance(EdgeAssistantMain.class);
        injector.getInstance(Initialization.class).setReady();

    }
}

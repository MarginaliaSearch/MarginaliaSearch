package nu.marginalia.wmsa.memex;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.gemini.GeminiConfigurationModule;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

public class MemexMain extends MainClass {
    private final MemexService service;

    @Inject
    public MemexMain(MemexService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.MEMEX, args);

        Injector injector = Guice.createInjector(
                new MemexConfigurationModule(),
                new GeminiConfigurationModule(),
                new ConfigurationModule());
        injector.getInstance(MemexMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}

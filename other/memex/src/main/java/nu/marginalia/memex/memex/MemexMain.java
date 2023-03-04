package nu.marginalia.memex.memex;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.memex.MemexServiceDescriptors;
import nu.marginalia.memex.gemini.GeminiConfigurationModule;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ConfigurationModule;
import nu.marginalia.service.server.Initialization;

public class MemexMain extends MainClass {
    private final MemexService service;

    @Inject
    public MemexMain(MemexService service) {
        this.service = service;
    }

    public static void main(String... args) {
        MainClass.init(ServiceId.Other_Memex, args);

        Injector injector = Guice.createInjector(
                new MemexConfigurationModule(),
                new GeminiConfigurationModule(),
                new ConfigurationModule(MemexServiceDescriptors.descriptors, ServiceId.Other_Memex));
        injector.getInstance(MemexMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}

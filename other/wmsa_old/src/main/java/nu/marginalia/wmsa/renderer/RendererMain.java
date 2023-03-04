package nu.marginalia.wmsa.renderer;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ConfigurationModule;
import nu.marginalia.service.server.Initialization;

public class RendererMain extends MainClass {
    private final RendererService service;

    @Inject
    public RendererMain(RendererService service
                              ) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceId.Other_Renderer, args);

        Injector injector = Guice.createInjector(
                new RendererModule(),
                new ConfigurationModule(WmsaServiceDescriptors.descriptors, ServiceId.Other_Renderer));
        injector.getInstance(RendererMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}

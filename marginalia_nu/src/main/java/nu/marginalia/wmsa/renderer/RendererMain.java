package nu.marginalia.wmsa.renderer;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

import java.io.IOException;

public class RendererMain extends MainClass {
    private RendererService service;

    @Inject
    public RendererMain(RendererService service
                              ) throws IOException {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.RENDERER, args);

        Injector injector = Guice.createInjector(
                new RendererModule(),
                new ConfigurationModule());
        injector.getInstance(RendererMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}

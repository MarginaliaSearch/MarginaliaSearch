package nu.marginalia.wmsa.smhi.scraper;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.smhi.SmhiScraperService;

import java.io.IOException;

public class SmhiScraperMain extends MainClass {
    private final SmhiScraperService service;

    @Inject
    public SmhiScraperMain(SmhiScraperService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.SMHI_SCRAPER, args);

        Injector injector = Guice.createInjector(
                new SmhiScraperModule(),
                new ConfigurationModule());
        injector.getInstance(SmhiScraperMain.class);
        injector.getInstance(Initialization.class).setReady();
    }

}

package nu.marginalia.wmsa.podcasts;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ConfigurationModule;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.wmsa.renderer.WmsaServiceDescriptors;

public class PodcastScraperMain extends MainClass {

    private final PodcastScraperService service;

    @Inject
    public PodcastScraperMain(PodcastScraperService service) {
        this.service = service;
    }

    public static void main(String... args) {

        init(ServiceId.Other_PodcastScraper, args);

        Injector injector = Guice.createInjector(
                new ConfigurationModule(WmsaServiceDescriptors.descriptors, ServiceId.Other_PodcastScraper));
        injector.getInstance(PodcastScraperMain.class);
        injector.getInstance(Initialization.class).setReady();
    }

}

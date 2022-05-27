package nu.marginalia.wmsa.encyclopedia;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;

public class EncyclopediaMain extends MainClass  {
    private final EncyclopediaService service;

    public static void main(String... args)  {
        init(ServiceDescriptor.ENCYCLOPEDIA, args);

        Injector injector = Guice.createInjector(
                new EncyclopediaModule(),
                new ConfigurationModule());
        injector.getInstance(EncyclopediaMain.class);
    }

    @Inject
    public EncyclopediaMain(EncyclopediaService service) {
        this.service = service;
    }

}

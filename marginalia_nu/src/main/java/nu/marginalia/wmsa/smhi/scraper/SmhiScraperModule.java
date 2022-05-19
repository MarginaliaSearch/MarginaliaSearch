package nu.marginalia.wmsa.smhi.scraper;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class SmhiScraperModule extends AbstractModule {
    public void configure() {
        bind(String.class).annotatedWith(Names.named("plats-csv-file")).toInstance("data/smhi/stader.csv");
        bind(String.class).annotatedWith(Names.named("smhi-user-agent")).toInstance("kontakt@marginalia.nu");
    }

}

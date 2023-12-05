package nu.marginalia.dating;

import com.google.inject.AbstractModule;
import nu.marginalia.renderer.config.DefaultHandlebarsConfigurator;
import nu.marginalia.renderer.config.HandlebarsConfigurator;

public class DatingModule extends AbstractModule  {
    public void configure() {
        bind(HandlebarsConfigurator.class).to(DefaultHandlebarsConfigurator.class);
    }
}

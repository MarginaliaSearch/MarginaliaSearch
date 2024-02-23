package nu.marginalia.control;

import com.google.inject.AbstractModule;
import nu.marginalia.renderer.config.HandlebarsConfigurator;

public class ControlProcessModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(HandlebarsConfigurator.class).to(ControlHandlebarsConfigurator.class);
    }
}

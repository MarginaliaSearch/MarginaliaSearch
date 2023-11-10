package nu.marginalia.control;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.name.Names;
import nu.marginalia.renderer.config.HandlebarsConfigurator;

import java.nio.file.Path;

public class ControlProcessModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(HandlebarsConfigurator.class).to(ControlHandlebarsConfigurator.class);
    }
}

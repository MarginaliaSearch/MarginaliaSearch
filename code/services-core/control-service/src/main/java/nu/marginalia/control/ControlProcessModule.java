package nu.marginalia.control;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.name.Names;

import java.nio.file.Path;

public class ControlProcessModule extends AbstractModule {
    @Override
    protected void configure() {
        String dist = System.getProperty("distPath", System.getProperty("WMSA_HOME", "/var/lib/wmsa") + "/dist/current");
        bind(Path.class).annotatedWith(Names.named("distPath")).toInstance(Path.of(dist));
    }
}

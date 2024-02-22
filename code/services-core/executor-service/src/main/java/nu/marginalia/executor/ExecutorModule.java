package nu.marginalia.executor;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.WmsaHome;

import java.nio.file.Path;

public class ExecutorModule extends AbstractModule  {
    public void configure() {

        String dist = System.getProperty("distPath", WmsaHome.getHomePath().resolve("/dist").toString());
        bind(Path.class).annotatedWith(Names.named("distPath")).toInstance(Path.of(dist));
    }
}

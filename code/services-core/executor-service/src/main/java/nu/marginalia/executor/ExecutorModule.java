package nu.marginalia.executor;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import java.nio.file.Path;

public class ExecutorModule extends AbstractModule  {
    public void configure() {
        String dist = System.getProperty("distPath", System.getProperty("WMSA_HOME", "/var/lib/wmsa") + "/dist/current");
        bind(Path.class).annotatedWith(Names.named("distPath")).toInstance(Path.of(dist));
    }
}

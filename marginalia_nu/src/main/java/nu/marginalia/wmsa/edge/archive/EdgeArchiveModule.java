package nu.marginalia.wmsa.edge.archive;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import java.nio.file.Path;

public class EdgeArchiveModule extends AbstractModule {
    public void configure() {
        bind(Path.class).annotatedWith(Names.named("archive-path")).toInstance(Path.of("/var/lib/wmsa/archive/webpage/"));
        bind(Path.class).annotatedWith(Names.named("wiki-path")).toInstance(Path.of("/var/lib/wmsa/archive.fast/wiki/"));
        bind(Integer.class).annotatedWith(Names.named("archive-size")).toInstance(10_000);
    }

}

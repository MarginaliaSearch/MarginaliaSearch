package nu.marginalia.index;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.WmsaHome;

import java.nio.file.Path;

public class IndexTablesModule extends AbstractModule {

    public void configure() {
        bind(Path.class).annotatedWith(Names.named("partition-root-slow")).toInstance(WmsaHome.getDisk("index-write"));
        bind(Path.class).annotatedWith(Names.named("partition-root-fast")).toInstance(WmsaHome.getDisk("index-read"));

        bind(Path.class).annotatedWith(Names.named("partition-root-slow-tmp")).toInstance(WmsaHome.getDisk("tmp-slow"));
        bind(Path.class).annotatedWith(Names.named("tmp-file-dir")).toInstance(WmsaHome.getDisk("tmp-fast"));

    }

}

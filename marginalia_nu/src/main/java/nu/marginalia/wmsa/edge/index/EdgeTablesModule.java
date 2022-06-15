package nu.marginalia.wmsa.edge.index;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.wmsa.configuration.WmsaHome;

import java.nio.file.Path;

public class EdgeTablesModule extends AbstractModule {

    public void configure() {
        bind(Path.class).annotatedWith(Names.named("partition-root-slow")).toInstance(WmsaHome.getDisk("index-write"));
        bind(Path.class).annotatedWith(Names.named("partition-root-fast")).toInstance(WmsaHome.getDisk("index-read"));

        bind(Path.class).annotatedWith(Names.named("partition-root-slow-tmp")).toInstance(WmsaHome.getDisk("tmp-slow"));
        bind(Path.class).annotatedWith(Names.named("tmp-file-dir")).toInstance(WmsaHome.getDisk("tmp-fast"));

        bind(String.class).annotatedWith(Names.named("edge-writer-page-index-file")).toInstance("page-index.dat");
        bind(String.class).annotatedWith(Names.named("edge-writer-dictionary-file")).toInstance("dictionary.dat");

        bind(String.class).annotatedWith(Names.named("edge-index-write-words-file")).toInstance("words.dat.wip");
        bind(String.class).annotatedWith(Names.named("edge-index-write-urls-file")).toInstance("urls.dat.wip");

        bind(String.class).annotatedWith(Names.named("edge-index-read-words-file")).toInstance("words.dat");
        bind(String.class).annotatedWith(Names.named("edge-index-read-urls-file")).toInstance("urls.dat");

    }

}

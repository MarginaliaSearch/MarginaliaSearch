package nu.marginalia.wmsa.edge.index;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import java.nio.file.Path;

public class EdgeTablesModule extends AbstractModule {

    public void configure() {
        bind(Path.class).annotatedWith(Names.named("partition-root-slow")).toInstance(Path.of("/var/lib/wmsa/index/write"));
        bind(Path.class).annotatedWith(Names.named("partition-root-slow-tmp")).toInstance(Path.of("/backup/work/index-tmp/"));

        bind(Path.class).annotatedWith(Names.named("partition-root-fast")).toInstance(Path.of("/var/lib/wmsa/index/read"));
        bind(Path.class).annotatedWith(Names.named("tmp-file-dir")).toInstance(Path.of("/var/lib/wmsa/index/read"));

        bind(String.class).annotatedWith(Names.named("edge-writer-page-index-file")).toInstance("page-index.dat");
        bind(String.class).annotatedWith(Names.named("edge-writer-dictionary-file")).toInstance("dictionary.dat");

        bind(String.class).annotatedWith(Names.named("edge-index-write-words-file")).toInstance("words.dat.wip");
        bind(String.class).annotatedWith(Names.named("edge-index-write-urls-file")).toInstance("urls.dat.wip");

        bind(String.class).annotatedWith(Names.named("edge-index-read-words-file")).toInstance("words.dat");
        bind(String.class).annotatedWith(Names.named("edge-index-read-urls-file")).toInstance("urls.dat");

    }

}

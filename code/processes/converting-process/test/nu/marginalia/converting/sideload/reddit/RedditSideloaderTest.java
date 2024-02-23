package nu.marginalia.converting.sideload.reddit;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.converting.ConverterModule;
import nu.marginalia.converting.processor.ConverterDomainTypes;
import nu.marginalia.converting.sideload.SideloadSourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.Mockito.when;

class RedditSideloaderTest extends AbstractModule {
    /* This test case exists for debugging, to get deep into the Reddit sideloader and see if it can read the files.
    *  Update the path to the Reddit database in the dbPath variable.
    * */
    private static final Path dbPath = Path.of("/home/vlofgren/Code/RemoteEnv/local/index-1/uploads/reddit/");

    private SideloadSourceFactory sourceFactory;
    @BeforeEach
    public void setUp() throws IOException {
        sourceFactory = Guice.createInjector(new ConverterModule(), this)
                .getInstance(SideloadSourceFactory.class);
    }

    public void configure() {
        var domainTypesMock = Mockito.mock(ConverterDomainTypes.class);
        when(domainTypesMock.isBlog(Mockito.any())).thenReturn(false);
        bind(ProcessConfiguration.class).toInstance(new ProcessConfiguration("test", 1, UUID.randomUUID()));
        bind(ConverterDomainTypes.class).toInstance(domainTypesMock);
    }

    @Test
    void getDocumentsStream() throws IOException {
        if (Files.notExists(dbPath)) {
            return;
        }

        var sideloader = sourceFactory.sideloadReddit(dbPath);

        sideloader.getDomain();
        var stream = sideloader.getDocumentsStream();
        for (int i = 0; i < 10; i++) {
            var next = stream.next();
            System.out.println(next);
        }
    }
}
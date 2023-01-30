package nu.marginalia.wmsa.edge.index.service;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.util.test.TestUtil;
import nu.marginalia.wmsa.edge.index.IndexServicesFactory;
import nu.marginalia.wmsa.edge.index.svc.EdgeIndexSearchSetsService;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSetAny;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSetIdentifier;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.mockito.Mockito.when;

public class EdgeIndexIntegrationTestModule extends AbstractModule {
    Path workDir;
    Path slowDir;
    Path fastDir;

    Random random = new Random();

    public EdgeIndexIntegrationTestModule() throws IOException {
        workDir = Files.createTempDirectory(EdgeIndexIntegrationTest.class.getSimpleName());
        slowDir = workDir.resolve("slow");
        fastDir = workDir.resolve("fast");

        Files.createDirectory(slowDir);
        Files.createDirectory(fastDir);
    }

    public void cleanUp() {
        TestUtil.clearTempDir(workDir);
    }

    @Override
    protected void configure() {

        System.setProperty("small-ram", "true");
        try {
            bind(IndexServicesFactory.class).toInstance(new IndexServicesFactory(Path.of("/tmp"),
                    slowDir, fastDir, null
            ));

            EdgeIndexSearchSetsService setsServiceMock = Mockito.mock(EdgeIndexSearchSetsService.class);
            when(setsServiceMock.getSearchSetByName(SearchSetIdentifier.NONE)).thenReturn(new SearchSetAny());

            bind(EdgeIndexSearchSetsService.class).toInstance(setsServiceMock);

            bind(String.class).annotatedWith(Names.named("service-host")).toInstance("127.0.0.1");
            bind(Integer.class).annotatedWith(Names.named("service-port")).toProvider(this::randomPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private int randomPort() {
        return random.nextInt(10000, 30000);
    }
}

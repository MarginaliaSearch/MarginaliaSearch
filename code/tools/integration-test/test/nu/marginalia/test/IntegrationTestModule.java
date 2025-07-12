package nu.marginalia.test;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.IndexLocations;
import nu.marginalia.LanguageModels;
import nu.marginalia.WmsaHome;
import nu.marginalia.api.domsample.DomSampleClient;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.index.domainrankings.DomainRankings;
import nu.marginalia.index.journal.IndexJournalSlopWriter;
import nu.marginalia.index.searchset.SearchSetAny;
import nu.marginalia.index.searchset.SearchSetsService;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.linkgraph.io.DomainLinksWriter;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.control.FakeProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.control.FakeServiceHeartbeat;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBase;
import nu.marginalia.storage.model.FileStorageBaseType;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static nu.marginalia.linkdb.LinkdbFileNames.DOCDB_FILE_NAME;
import static nu.marginalia.linkdb.LinkdbFileNames.DOMAIN_LINKS_FILE_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class IntegrationTestModule extends AbstractModule {
    Path workDir;
    Path slowDir;
    Path fastDir;
    Path indexDir;

    Random random = new Random();

    public IntegrationTestModule() throws IOException {
        workDir = Files.createTempDirectory("IntegrationTest");
        slowDir = workDir.resolve("slow");
        fastDir = workDir.resolve("fast");
        indexDir = workDir.resolve("index");

        Files.createDirectory(slowDir);
        Files.createDirectory(fastDir);
    }

    public void cleanUp() {
        TestUtil.clearTempDir(workDir);
    }

    @Override
    protected void configure() {

        try {
            var fileStorageServiceMock = Mockito.mock(FileStorageService.class);
            Mockito.when(fileStorageServiceMock.getStorageBase(FileStorageBaseType.WORK))
                    .thenReturn(new FileStorageBase(null, null, 0,null, slowDir.toString()));
            Mockito.when(fileStorageServiceMock.getStorageBase(FileStorageBaseType.CURRENT))
                    .thenReturn(new FileStorageBase(null, null, 0,null, fastDir.toString()));
            Mockito.when(fileStorageServiceMock.getStorageBase(FileStorageBaseType.STORAGE))
                    .thenReturn(new FileStorageBase(null, null, 0, null, fastDir.toString()));

            bind(DocumentDbReader.class).toInstance(new DocumentDbReader(
                    IndexLocations.getLinkdbLivePath(fileStorageServiceMock)
                            .resolve(DOCDB_FILE_NAME)
            ));

            bind(FileStorageService.class).toInstance(fileStorageServiceMock);
            bind(ServiceHeartbeat.class).toInstance(new FakeServiceHeartbeat());
            bind(ProcessHeartbeat.class).toInstance(new FakeProcessHeartbeat());
            DomSampleClient domSampleClientMock = Mockito.mock(DomSampleClient.class);
            when(domSampleClientMock.getSampleAsync(any(), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
            bind(DomSampleClient.class).toInstance(domSampleClientMock);

            SearchSetsService setsServiceMock = Mockito.mock(SearchSetsService.class);
            when(setsServiceMock.getSearchSetByName("NONE")).thenReturn(new SearchSetAny());
            when(setsServiceMock.getDomainRankings()).thenReturn(new DomainRankings());
            bind(SearchSetsService.class).toInstance(setsServiceMock);

            DomainTypes domainTypes = Mockito.mock(DomainTypes.class);
            when(domainTypes.getAllDomainsByType(any())).thenReturn(new ArrayList<>());
            when(domainTypes.getKnownDomainsByType(any())).thenReturn(new TIntArrayList());
            when(domainTypes.downloadList(any())).thenReturn(new ArrayList<>());
            bind(DomainTypes.class).toInstance(domainTypes);

            bind(ServiceEventLog.class).toInstance(Mockito.mock(ServiceEventLog.class));

            bind(IndexJournalSlopWriter.class).toInstance(new IndexJournalSlopWriter(
                    IndexLocations.getIndexConstructionArea(fileStorageServiceMock),
                    0
            ));

            bind(ServiceConfiguration.class).toInstance(new ServiceConfiguration(
                    ServiceId.Index,
                    0,
                    "127.0.0.1",
                    "127.0.0.1",
                    randomPort(),
                    UUID.randomUUID()
            ));

            bind(ProcessConfiguration.class).toInstance(new ProcessConfiguration(
                    "TEST",
                    0,
                    UUID.randomUUID()));

            bind(Double.class).annotatedWith(Names.named("min-document-quality")).toInstance(-15.);
            bind(Integer.class).annotatedWith(Names.named("min-document-length")).toInstance(32);
            bind(Integer.class).annotatedWith(Names.named("max-title-length")).toInstance(128);
            bind(Integer.class).annotatedWith(Names.named("max-summary-length")).toInstance(255);

            bind(Path.class).annotatedWith(Names.named("local-index-path")).toInstance(indexDir);

            bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());

        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }


    }


    @Inject
    @Provides
    @Singleton
    private DocumentDbWriter createLinkdbWriter(FileStorageService service) throws SQLException, IOException {
        // Migrate
        Path dbPath = IndexLocations.getLinkdbWritePath(service).resolve(DOCDB_FILE_NAME);

        if (Files.exists(dbPath)) {
            Files.delete(dbPath);
        }
        return new DocumentDbWriter(dbPath);
    }

    @Inject @Provides @Singleton
    private DomainLinksWriter createDomainLinkdbWriter(FileStorageService service) throws SQLException, IOException {

        Path dbPath = IndexLocations.getLinkdbWritePath(service).resolve(DOMAIN_LINKS_FILE_NAME);

        if (Files.exists(dbPath)) {
            Files.delete(dbPath);
        }

        return new DomainLinksWriter(dbPath);
    }
    private int randomPort() {
        return random.nextInt(10000, 30000);
    }
}

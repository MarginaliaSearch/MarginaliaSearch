package nu.marginalia.index.svc;

import com.google.inject.AbstractModule;
import nu.marginalia.IndexLocations;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBase;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.index.journal.writer.IndexJournalWriterPagingImpl;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.process.control.FakeProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.ranking.DomainRankings;
import nu.marginalia.index.svc.searchset.SearchSetAny;
import nu.marginalia.index.util.TestUtil;
import nu.marginalia.service.control.*;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ServiceConfiguration;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;

import static nu.marginalia.linkdb.LinkdbFileNames.DOCDB_FILE_NAME;
import static org.mockito.Mockito.when;

public class IndexQueryServiceIntegrationTestModule extends AbstractModule {
    Path workDir;
    Path slowDir;
    Path fastDir;

    Random random = new Random();

    public IndexQueryServiceIntegrationTestModule() throws IOException {
        workDir = Files.createTempDirectory(IndexQueryServiceIntegrationSmokeTest.class.getSimpleName());
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

        try {
            var fileStorageServiceMock = Mockito.mock(FileStorageService.class);
            Mockito.when(fileStorageServiceMock.getStorageBase(FileStorageBaseType.WORK)).thenReturn(new FileStorageBase(null, null, 0,null, slowDir.toString()));
            Mockito.when(fileStorageServiceMock.getStorageBase(FileStorageBaseType.CURRENT)).thenReturn(new FileStorageBase(null, null, 0,null, fastDir.toString()));
            Mockito.when(fileStorageServiceMock.getStorageBase(FileStorageBaseType.STORAGE)).thenReturn(new FileStorageBase(null, null, 0, null, fastDir.toString()));

            bind(DocumentDbReader.class).toInstance(new DocumentDbReader(
                    IndexLocations.getLinkdbLivePath(fileStorageServiceMock)
                            .resolve(DOCDB_FILE_NAME)
            ));

            bind(FileStorageService.class).toInstance(fileStorageServiceMock);

            bind(ServiceHeartbeat.class).toInstance(new FakeServiceHeartbeat());
            bind(ProcessHeartbeat.class).toInstance(new FakeProcessHeartbeat());

            IndexSearchSetsService setsServiceMock = Mockito.mock(IndexSearchSetsService.class);
            when(setsServiceMock.getSearchSetByName("NONE")).thenReturn(new SearchSetAny());
            when(setsServiceMock.getDomainRankings()).thenReturn(new DomainRankings());
            bind(IndexSearchSetsService.class).toInstance(setsServiceMock);

            bind(ServiceEventLog.class).toInstance(Mockito.mock(ServiceEventLog.class));

            bind(IndexJournalWriter.class).toInstance(new IndexJournalWriterPagingImpl(
                    IndexLocations.getIndexConstructionArea(fileStorageServiceMock)
            ));

            bind(ServiceConfiguration.class).toInstance(new ServiceConfiguration(
                    ServiceId.Index,
                    0,
                    "127.0.0.1",
                    "127.0.0.1",
                    randomPort(),
                    UUID.randomUUID()
            ));

        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }


    }

    private int randomPort() {
        return random.nextInt(10000, 30000);
    }
}

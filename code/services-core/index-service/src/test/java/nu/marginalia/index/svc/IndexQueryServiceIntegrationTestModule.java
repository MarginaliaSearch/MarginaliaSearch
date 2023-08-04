package nu.marginalia.index.svc;

import com.google.inject.AbstractModule;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.index.IndexServicesFactory;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.index.journal.writer.IndexJournalWriterImpl;
import nu.marginalia.lexicon.KeywordLexicon;
import nu.marginalia.lexicon.KeywordLexiconReadOnlyView;
import nu.marginalia.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.lexicon.journal.KeywordLexiconJournalMode;
import nu.marginalia.ranking.DomainRankings;
import nu.marginalia.index.svc.searchset.SearchSetAny;
import nu.marginalia.index.util.TestUtil;
import nu.marginalia.index.client.model.query.SearchSetIdentifier;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ServiceConfiguration;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;

import static org.mockito.Mockito.when;

public class IndexQueryServiceIntegrationTestModule extends AbstractModule {
    Path workDir;
    Path slowDir;
    Path fastDir;

    Random random = new Random();

    public IndexQueryServiceIntegrationTestModule() throws IOException {
        workDir = Files.createTempDirectory(IndexQueryServiceIntegrationTest.class.getSimpleName());
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

            when(fileStorageServiceMock.getStorageByType(FileStorageType.SEARCH_SETS)).thenReturn(new FileStorage(null, null, null, fastDir.toString(), null));
            when(fileStorageServiceMock.getStorageByType(FileStorageType.LEXICON_LIVE)).thenReturn(new FileStorage(null, null, null, fastDir.toString(), null));
            when(fileStorageServiceMock.getStorageByType(FileStorageType.LEXICON_STAGING)).thenReturn(new FileStorage(null, null, null, fastDir.toString(), null));
            when(fileStorageServiceMock.getStorageByType(FileStorageType.INDEX_LIVE)).thenReturn(new FileStorage(null, null, null, fastDir.toString(), null));
            when(fileStorageServiceMock.getStorageByType(FileStorageType.INDEX_STAGING)).thenReturn(new FileStorage(null, null, null, slowDir.toString(), null));

            var servicesFactory = new IndexServicesFactory(
                    Mockito.mock(ServiceHeartbeat.class),
                    fileStorageServiceMock
            );
            bind(IndexServicesFactory.class).toInstance(servicesFactory);

            IndexSearchSetsService setsServiceMock = Mockito.mock(IndexSearchSetsService.class);
            when(setsServiceMock.getSearchSetByName(SearchSetIdentifier.NONE)).thenReturn(new SearchSetAny());
            when(setsServiceMock.getDomainRankings()).thenReturn(new DomainRankings());
            bind(IndexSearchSetsService.class).toInstance(setsServiceMock);

            var keywordLexicon = new KeywordLexicon(new KeywordLexiconJournal(
                    slowDir.resolve("dictionary.dat").toFile(),
                    KeywordLexiconJournalMode.READ_WRITE)
            );
            bind(KeywordLexicon.class).toInstance(keywordLexicon);
            bind(KeywordLexiconReadOnlyView.class).toInstance(new KeywordLexiconReadOnlyView(keywordLexicon));

            bind(ServiceEventLog.class).toInstance(Mockito.mock(ServiceEventLog.class));
            bind(ServiceHeartbeat.class).toInstance(Mockito.mock(ServiceHeartbeat.class));

            bind(IndexJournalWriter.class).toInstance(new IndexJournalWriterImpl(keywordLexicon,
                    slowDir.resolve("page-index.dat")));

            bind(ServiceConfiguration.class).toInstance(new ServiceConfiguration(
                    ServiceId.Index,
                    0,
                    "127.0.0.1",
                    randomPort(),
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

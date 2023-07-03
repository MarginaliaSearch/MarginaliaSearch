package nu.marginalia.index.svc;

import com.google.inject.AbstractModule;
import nu.marginalia.index.IndexServicesFactory;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.lexicon.KeywordLexicon;
import nu.marginalia.lexicon.KeywordLexiconReadOnlyView;
import nu.marginalia.lexicon.journal.KeywordLexiconJournal;
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
            var servicesFactory = new IndexServicesFactory(Path.of("/tmp"),
                    slowDir, fastDir
            );
            bind(IndexServicesFactory.class).toInstance(servicesFactory);

            IndexSearchSetsService setsServiceMock = Mockito.mock(IndexSearchSetsService.class);
            when(setsServiceMock.getSearchSetByName(SearchSetIdentifier.NONE)).thenReturn(new SearchSetAny());
            when(setsServiceMock.getDomainRankings()).thenReturn(new DomainRankings());
            bind(IndexSearchSetsService.class).toInstance(setsServiceMock);

            var keywordLexicon = new KeywordLexicon(new KeywordLexiconJournal(slowDir.resolve("dictionary.dat").toFile()));
            bind(KeywordLexicon.class).toInstance(keywordLexicon);
            bind(KeywordLexiconReadOnlyView.class).toInstance(new KeywordLexiconReadOnlyView(keywordLexicon));

            bind(IndexJournalWriter.class).toInstance(servicesFactory.createIndexJournalWriter(keywordLexicon));

            bind(ServiceEventLog.class).toInstance(Mockito.mock(ServiceEventLog.class));
            bind(ServiceHeartbeat.class).toInstance(Mockito.mock(ServiceHeartbeat.class));

            bind(ServiceConfiguration.class).toInstance(new ServiceConfiguration(
                    ServiceId.Index,
                    0,
                    "127.0.0.1",
                    randomPort(),
                    randomPort(),
                    UUID.randomUUID()
            ));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private int randomPort() {
        return random.nextInt(10000, 30000);
    }
}

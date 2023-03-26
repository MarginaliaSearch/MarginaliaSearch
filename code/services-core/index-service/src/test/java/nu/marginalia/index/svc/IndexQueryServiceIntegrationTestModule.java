package nu.marginalia.index.svc;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.WmsaHome;
import nu.marginalia.index.IndexServicesFactory;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.index.journal.writer.IndexJournalWriterImpl;
import nu.marginalia.lexicon.KeywordLexicon;
import nu.marginalia.lexicon.KeywordLexiconReadOnlyView;
import nu.marginalia.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.ranking.DomainRankings;
import nu.marginalia.index.svc.searchset.SearchSetAny;
import nu.marginalia.index.util.TestUtil;
import nu.marginalia.index.client.model.query.SearchSetIdentifier;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

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

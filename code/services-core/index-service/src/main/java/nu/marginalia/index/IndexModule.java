package nu.marginalia.index;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.index.config.RankingSettings;
import nu.marginalia.WmsaHome;
import nu.marginalia.lexicon.KeywordLexicon;
import nu.marginalia.lexicon.KeywordLexiconReadOnlyView;
import nu.marginalia.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.service.control.ServiceEventLog;

import java.nio.file.Path;

public class IndexModule extends AbstractModule {



    public void configure() {
    }

    @Provides
    @SneakyThrows
    @Singleton
    private KeywordLexiconReadOnlyView createLexicon(ServiceEventLog eventLog, FileStorageService fileStorageService) {
        try {
            eventLog.logEvent("INDEX-LEXICON-LOAD-BEGIN", "");

            var area = fileStorageService.getStorageByType(FileStorageType.LEXICON_LIVE);
            var path = area.asPath().resolve("dictionary.dat");

            return new KeywordLexiconReadOnlyView(new KeywordLexicon(new KeywordLexiconJournal(path.toFile())));
        }
        finally {
            eventLog.logEvent("INDEX-LEXICON-LOAD-OK", "");
        }
    }

    @Provides
    public RankingSettings rankingSettings() {
        Path dir = WmsaHome.getHomePath().resolve("conf/ranking-settings.yaml");
        return RankingSettings.from(dir);
    }

}

package nu.marginalia.index;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import lombok.SneakyThrows;
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
    private KeywordLexiconReadOnlyView createLexicon(ServiceEventLog eventLog) {
        try {
            eventLog.logEvent("INDEX-LEXICON-LOAD-BEGIN", "");

            return new KeywordLexiconReadOnlyView(
                    new KeywordLexicon(
                            new KeywordLexiconJournal(WmsaHome.getDisk("index-write").resolve("dictionary.dat").toFile()
                            )
                    )
            );
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

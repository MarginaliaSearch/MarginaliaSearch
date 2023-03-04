package nu.marginalia.index;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import lombok.SneakyThrows;
import nu.marginalia.index.config.RankingSettings;
import nu.marginalia.WmsaHome;
import nu.marginalia.lexicon.KeywordLexicon;
import nu.marginalia.lexicon.KeywordLexiconReadOnlyView;
import nu.marginalia.lexicon.journal.KeywordLexiconJournal;

import java.nio.file.Path;

public class IndexModule extends AbstractModule {



    public void configure() {
    }

    @Provides
    @SneakyThrows
    private KeywordLexiconReadOnlyView createLexicon() {
        return new KeywordLexiconReadOnlyView(
                new KeywordLexicon(
                    new KeywordLexiconJournal(WmsaHome.getDisk("index-write").resolve("dictionary.dat").toFile()
                    )
                )
        );
    }

    @Provides
    public RankingSettings rankingSettings() {
        Path dir = WmsaHome.getHomePath().resolve("conf/ranking-settings.yaml");
        return RankingSettings.from(dir);
    }

}

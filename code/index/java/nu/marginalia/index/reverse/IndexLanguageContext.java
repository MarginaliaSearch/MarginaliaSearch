package nu.marginalia.index.reverse;

import javax.annotation.Nullable;

public class IndexLanguageContext {
    public final String languageIsoCode;

    @Nullable
    final WordLexicon wordLexiconFull;

    @Nullable
    final WordLexicon wordLexiconPrio;

    public IndexLanguageContext(String languageIsoCode, WordLexicon wordLexiconFull, WordLexicon wordLexiconPrio) {
        this.languageIsoCode = languageIsoCode;
        this.wordLexiconFull = wordLexiconFull;
        this.wordLexiconPrio = wordLexiconPrio;
    }
}

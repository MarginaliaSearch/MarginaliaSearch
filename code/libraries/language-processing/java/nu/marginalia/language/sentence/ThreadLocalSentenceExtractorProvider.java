package nu.marginalia.language.sentence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.config.LanguageConfiguration;

@Singleton
public class ThreadLocalSentenceExtractorProvider {
    private final ThreadLocal<SentenceExtractor> sentenceExtractorThreadLocal;

    @Inject
    public ThreadLocalSentenceExtractorProvider(LanguageConfiguration languageConfiguration, LanguageModels languageModels) {
        sentenceExtractorThreadLocal = ThreadLocal.withInitial(() -> new SentenceExtractor(languageConfiguration, languageModels));
    }

    public SentenceExtractor get() {
        return sentenceExtractorThreadLocal.get();
    }
}

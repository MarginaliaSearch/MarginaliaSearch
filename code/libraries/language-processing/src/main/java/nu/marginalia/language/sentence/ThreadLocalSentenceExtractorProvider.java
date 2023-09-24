package nu.marginalia.language.sentence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.LanguageModels;

@Singleton
public class ThreadLocalSentenceExtractorProvider {
    private final ThreadLocal<SentenceExtractor> sentenceExtractorThreadLocal;

    @Inject
    public ThreadLocalSentenceExtractorProvider(LanguageModels languageModels) {
        sentenceExtractorThreadLocal = ThreadLocal.withInitial(() -> new SentenceExtractor(languageModels));
    }

    public SentenceExtractor get() {
        return sentenceExtractorThreadLocal.get();
    }
}

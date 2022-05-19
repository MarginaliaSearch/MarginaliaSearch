package nu.marginalia.wmsa.edge.search.query;

import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import org.junit.jupiter.api.Test;

class EnglishDictionaryTest {

    @Test
    void getWordVariants() {
        LanguageModels lm = TestLanguageModels.getLanguageModels();

        var dict = new NGramDict(lm);
        new EnglishDictionary(dict).getWordVariants("dos").forEach(System.out::println);
    }
}
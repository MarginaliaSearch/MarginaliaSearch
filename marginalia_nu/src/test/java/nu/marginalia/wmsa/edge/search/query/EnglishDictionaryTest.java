package nu.marginalia.wmsa.edge.search.query;

import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnglishDictionaryTest {

    @Test
    void getWordVariants() {
        LanguageModels lm = new LanguageModels(
                Path.of("/home/vlofgren/Work/ngrams/ngrams-generous-emstr.bin"),
                Path.of("/home/vlofgren/Work/ngrams/tfreq-new-algo4.bin"),
                Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin"),
                Path.of("/home/vlofgren/Work/ngrams/English.RDR"),
                Path.of("/home/vlofgren/Work/ngrams/English.DICT"),
                Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin")
        );

        var dict = new NGramDict(lm);
        new EnglishDictionary(dict).getWordVariants("dos").forEach(System.out::println);
    }
}
package nu.marginalia.wmsa.edge.assistant.suggest;

import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.assistant.dict.SpellChecker;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

class SuggestionsTest {
    private static Suggestions suggestions;

    @BeforeAll
    public static void setUp() {
        LanguageModels lm = new LanguageModels(
                Path.of("/home/vlofgren/Work/ngrams/ngrams-generous-emstr.bin"),
                Path.of("/home/vlofgren/Work/ngrams/tfreq-new-algo3.bin"),
                Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin"),
                Path.of("/home/vlofgren/Work/ngrams/English.RDR"),
                Path.of("/home/vlofgren/Work/ngrams/English.DICT"),
                Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin")
        );
        suggestions = new Suggestions(Path.of("/home/vlofgren/Work/sql-titles-clean"),
                new SpellChecker(), new NGramDict(lm));
    }

    @Test
    void getSuggestions() {
        System.out.println(tryGetSuggestions("neop"));
        System.out.println(tryGetSuggestions("neopla"));
        System.out.println(tryGetSuggestions("middle p"));
        System.out.println(tryGetSuggestions("new public mana"));
        System.out.println(tryGetSuggestions("euse"));
    }

    List<String> tryGetSuggestions(String s) {
        long start = System.currentTimeMillis();
        try {
            return suggestions.getSuggestions(10, s);
        }
        finally {
            System.out.println(System.currentTimeMillis() - start);
        }
    }
}
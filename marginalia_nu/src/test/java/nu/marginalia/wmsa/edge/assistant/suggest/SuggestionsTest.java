package nu.marginalia.wmsa.edge.assistant.suggest;

import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.assistant.dict.SpellChecker;
import nu.marginalia.util.language.conf.LanguageModels;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

class SuggestionsTest {
    private static Suggestions suggestions;

    @BeforeAll
    public static void setUp() {
        LanguageModels lm = TestLanguageModels.getLanguageModels();
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
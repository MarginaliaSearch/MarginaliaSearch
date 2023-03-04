package nu.marginalia.assistant.dict;

import com.google.inject.Singleton;
import symspell.SymSpell;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class SpellChecker {

    private final SymSpell symSpell = new SymSpell();

    public SpellChecker() {

    }

    public List<String> correct(String word) {
        return symSpell.Correct(word).stream().sorted(Comparator.comparing(term -> term.distance)).map(term->term.term).collect(Collectors.toList());
    }
}

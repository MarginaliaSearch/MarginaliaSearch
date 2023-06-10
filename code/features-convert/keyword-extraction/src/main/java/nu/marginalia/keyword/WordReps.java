package nu.marginalia.keyword;

import nu.marginalia.language.model.WordRep;

import java.util.Collection;

public interface WordReps {
    Collection<WordRep> getReps();

    default Collection<String> words() {
        return getReps().stream().map(rep -> rep.word).toList();
    }
}

package nu.marginalia.api.math.model;

import java.util.List;

public record DictionaryResponse(String word, List<DictionaryEntry> entries) {
    public DictionaryResponse(String word, List<DictionaryEntry> entries) {
        this.word = word;
        this.entries = entries.stream().toList(); // Make an immutable copy
    }
}

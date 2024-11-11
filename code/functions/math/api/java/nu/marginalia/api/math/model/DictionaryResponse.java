package nu.marginalia.api.math.model;

import java.util.List;

public class DictionaryResponse {
    public String word;
    public List<DictionaryEntry> entries;

    public DictionaryResponse(String word, List<DictionaryEntry> entries) {
        this.word = word;
        this.entries = entries;
    }

    public DictionaryResponse() {
    }

    public String getWord() {
        return this.word;
    }

    public List<DictionaryEntry> getEntries() {
        return this.entries;
    }

    public String toString() {
        return "DictionaryResponse(word=" + this.getWord() + ", entries=" + this.getEntries() + ")";
    }
}

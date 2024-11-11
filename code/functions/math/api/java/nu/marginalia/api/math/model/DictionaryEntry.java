package nu.marginalia.api.math.model;

public class DictionaryEntry {
    public final String type;
    public final String word;
    public final String definition;

    public DictionaryEntry(String type, String word, String definition) {
        this.type = type;
        this.word = word;
        this.definition = definition;
    }

    public String getType() {
        return this.type;
    }

    public String getWord() {
        return this.word;
    }

    public String getDefinition() {
        return this.definition;
    }

    public String toString() {
        return "DictionaryEntry(type=" + this.getType() + ", word=" + this.getWord() + ", definition=" + this.getDefinition() + ")";
    }
}

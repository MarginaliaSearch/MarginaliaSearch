package nu.marginalia.keyword;

import nu.marginalia.language.model.DocumentSentence;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

public record LinkTexts(List<DocumentSentence> linkTexts) implements Iterable<DocumentSentence> {
    public LinkTexts() {
        this(List.of());
    }

    @NotNull
    @Override
    public Iterator<DocumentSentence> iterator() {
        return linkTexts.iterator();
    }
}

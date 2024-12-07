package nu.marginalia.keyword;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.language.model.DocumentSentence;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

public record LinkTexts(
        List<DocumentSentence> linkTexts,
        TIntList counts
) implements Iterable<DocumentSentence> {
    public LinkTexts() {
        this(List.of(), new TIntArrayList());
    }

    public int length() {
        return linkTexts.size();
    }

    @NotNull
    @Override
    public Iterator<DocumentSentence> iterator() {
        return linkTexts.iterator();
    }
}

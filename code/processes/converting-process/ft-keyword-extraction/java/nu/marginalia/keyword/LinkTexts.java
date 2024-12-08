package nu.marginalia.keyword;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.language.model.DocumentSentence;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record LinkTexts(
        List<DocumentSentence> linkTexts,
        TIntList counts
) {
    public LinkTexts() {
        this(List.of(), new TIntArrayList());
    }

    public int length() {
        return linkTexts.size();
    }

    @NotNull
    public LinkTexts.Iter iterator() {
        return new Iter();
    }

    public class Iter {
        private int pos = -1;

        public boolean next() {
            return ++pos < length();
        }
        public int count() {
            return counts.get(pos);
        }
        public DocumentSentence sentence() {
            return linkTexts.get(pos);
        }
    }
}

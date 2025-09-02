package nu.marginalia.index.results.model.ids;

import nu.marginalia.index.reverse.positions.TermData;
import nu.marginalia.sequence.CodedSequence;

import javax.annotation.Nullable;
import java.util.Arrays;

public final class TermMetadataList {
    private final TermData[] array;

    public TermMetadataList(TermData[] array) {
        this.array = array;
    }

    public int size() {
        return array.length;
    }

    public long flag(int i) {
        if (array[i] == null)
            return 0;

        return array[i].flags();
    }

    /** Returns the position data for the given document index,
     * may be null if the term is not in the document
     */
    @Nullable
    public CodedSequence position(int i) {
        if (array[i] == null)
            return null;

        return array[i].positions();
    }

    public TermData[] array() {
        return array;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (TermMetadataList) obj;
        return Arrays.equals(this.array, that.array);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

}

package nu.marginalia.index.model;

import nu.marginalia.sequence.CodedSequence;

import javax.annotation.Nullable;
import java.util.BitSet;

public final class TermMetadataList {
    private final CodedSequence[] positions;
    private final byte[] flags;
    private final BitSet viableDocuments;

    public TermMetadataList(CodedSequence[] positions, byte[] flags, BitSet viableDocuments) {
        this.positions = positions;
        this.flags = flags;
        this.viableDocuments = viableDocuments;
    }

    public int size() {
        return positions.length;
    }

    public long flag(int i) {
        return flags[i];
    }

    /** Returns the position data for the given document index,
     * may be null if the term is not in the document
     */
    @Nullable
    public CodedSequence position(int i) {
        if (positions[i] == null)
            return null;

        return positions[i];
    }

    public BitSet viableDocuments() {
        return viableDocuments;
    }
}

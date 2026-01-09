package nu.marginalia.index.model;

import nu.marginalia.sequence.CodedSequence;

import javax.annotation.Nullable;
import java.util.BitSet;

public class CombinedTermMetadata {
    private final PositionDataOffsets positionDataOffsets;
    private final DocumentTermFlags flags;
    private final BitSet[] priorityTermsPresent;
    private final BitSet viableDocuments;

    public CombinedTermMetadata(PositionDataOffsets positionDataOffsets,
                                DocumentTermFlags flags,
                                BitSet[] priorityTermsPresent,
                                BitSet viableDocuments) {
        this.positionDataOffsets = positionDataOffsets;
        this.flags = flags;
        this.priorityTermsPresent = priorityTermsPresent;
        this.viableDocuments = viableDocuments;
    }


    public BitSet viableDocuments() {
        return viableDocuments;
    }

    public BitSet priorityTermsPresent(int pos) {
        BitSet ret = new BitSet(priorityTermsPresent.length);
        for (int i = 0; i < priorityTermsPresent.length; i++) {
            if (priorityTermsPresent[i].get(pos))
                ret.set(i);
        }
        return ret;
    }

    public long[] flagsForDoc(int docIdx) {
        return flags.get(docIdx);
    }

    public long[] positionOffsetsForDoc(int docIdx) {
        return positionDataOffsets.offsetsForDoc(docIdx, null);
    }
}

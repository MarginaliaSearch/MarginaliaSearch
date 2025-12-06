package nu.marginalia.index.model;

import nu.marginalia.sequence.CodedSequence;

import javax.annotation.Nullable;
import java.util.BitSet;

public class CombinedTermMetadata {
    private final TermMetadataList[] termMetadataLists;
    private final BitSet[] priorityTermsPresent;
    private final BitSet viableDocuments;

    public CombinedTermMetadata(TermMetadataList[] termMetadataLists,
                                BitSet[] priorityTermsPresent,
                                BitSet viableDocuments) {
        this.termMetadataLists = termMetadataLists;
        this.priorityTermsPresent = priorityTermsPresent;
        this.viableDocuments = viableDocuments;
    }


    public BitSet viableDocuments() {
        return viableDocuments;
    }

    public boolean priorityTermsPresent(int priorityTermId, int pos) {
        var bs = priorityTermsPresent[priorityTermId];
        if (bs == null)
            return false;
        return bs.get(pos);
    }


    public TermMetadataList termMetadata(int termId) {
        return termMetadataLists[termId];
    }

    public static final class TermMetadataList {
        private final CodedSequence[] positions;
        private final byte[] flags;

        public TermMetadataList(CodedSequence[] positions,
                                byte[] flags)
        {
            this.positions = positions;
            this.flags = flags;
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


    }
}

package nu.marginalia.index.results.model;

import it.unimi.dsi.fastutil.ints.IntIterator;
import nu.marginalia.index.forward.spans.DocumentSpan;
import nu.marginalia.index.model.SearchTermsUtil;
import nu.marginalia.index.results.model.ids.TermIdList;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.sequence.SequenceOperations;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * wordIds that we require to be in the same sentence
 */
public class PhraseConstraintGroupList {
    List<PhraseConstraintGroup> mandatoryGroups = new ArrayList<>();
    List<PhraseConstraintGroup> optionalGroups = new ArrayList<>();
    PhraseConstraintGroup fullGroup;

    public PhraseConstraintGroupList(
            PhraseConstraintGroup fullGroup,
            List<PhraseConstraintGroup> mandatoryGroups,
            List<PhraseConstraintGroup> optionalGroups) {
        this.mandatoryGroups.addAll(mandatoryGroups);
        this.optionalGroups.addAll(optionalGroups);
        this.fullGroup = fullGroup;
    }

    public List<PhraseConstraintGroup> getOptionalGroups() {
        return Collections.unmodifiableList(optionalGroups);
    }

    public PhraseConstraintGroup getFullGroup() {
        return fullGroup;
    }

    public boolean testMandatory(CodedSequence[] positions) {

        for (var constraint : mandatoryGroups) {
            if (!constraint.test(positions)) {
                return false;
            }
        }

        return true;
    }

    public int testOptional(CodedSequence[] positions) {

        int best = 0;
        for (var constraint : optionalGroups) {
            if (constraint.test(positions)) {
                best = Math.max(constraint.size, best);
            }
        }
        return best;
    }

    public int countOptional(CodedSequence[] positions) {

        int ct = 0;
        for (var constraint : optionalGroups) {
            if (constraint.test(positions)) {
                ct++;
            }
        }
        return ct;
    }

    public int testOptional(CodedSequence[] positions, DocumentSpan span) {

        int best = 0;
        for (var constraint : optionalGroups) {
            if (constraint.test(span, positions)) {
                best = Math.max(constraint.size, best);
            }
        }
        return best;
    }

    public boolean allOptionalInSpan(CodedSequence[] positions, DocumentSpan span) {
        for (var constraint : optionalGroups) {
            if (!constraint.test(span, positions)) {
                return false;
            }
        }
        return true;
    }

    public int numOptional() {
        return optionalGroups.size();
    }

    public static final class PhraseConstraintGroup {
        private final int[] offsets;
        private final BitSet present;
        private final BitSet termIdsMask;

        public final int size;
        public PhraseConstraintGroup(List<String> terms, TermIdList termIdsAll) {
            offsets = new int[terms.size()];
            present = new BitSet(terms.size());
            size = terms.size();

            termIdsMask = new BitSet(termIdsAll.size());

            int i = 0;
            for (String term : terms) {
                if (term.isEmpty()) {
                    continue;
                }

                present.set(i);
                long termId = SearchTermsUtil.getWordId(term);

                int idx = termIdsAll.indexOf(termId);
                if (idx < 0) {
                    offsets[i++] = -1;
                }
                else {
                    offsets[i++] = idx;
                    termIdsMask.set(idx);
                }
            }
        }

        /** Returns true if the term with index termIdx in the query is in the group */
        public boolean containsTerm(int termIdx) {
            return termIdsMask.get(termIdx);
        }

        public boolean test(CodedSequence[] positions) {
            IntIterator[] sequences = new IntIterator[present.cardinality()];

            for (int oi = 0, si = 0; oi < offsets.length; oi++) {
                if (!present.get(oi)) {
                    continue;
                }
                int offset = offsets[oi];
                if (offset < 0)
                    return false;

                // Create iterators that are offset by their relative position in the
                // sequence.  This is done by subtracting the index from the offset,
                // so that when we intersect them, an overlap means that the terms are
                // in the correct order.  Note the offset is negative!

                var posForTerm = positions[offset];
                if (posForTerm == null) {
                    return false;
                }
                sequences[si++] = posForTerm.offsetIterator(-oi);
            }

            return SequenceOperations.intersectSequences(sequences);
        }


        public boolean test(DocumentSpan span, CodedSequence[] positions) {
            IntIterator[] sequences = new IntIterator[present.cardinality()];

            for (int oi = 0, si = 0; oi < offsets.length; oi++) {
                if (!present.get(oi)) {
                    continue;
                }
                int offset = offsets[oi];
                if (offset < 0)
                    return false;

                // Create iterators that are offset by their relative position in the
                // sequence.  This is done by subtracting the index from the offset,
                // so that when we intersect them, an overlap means that the terms are
                // in the correct order.  Note the offset is negative!

                var posForTerm = positions[offset];
                if (posForTerm == null) {
                    return false;
                }
                sequences[si++] = posForTerm.offsetIterator(-oi);
            }

            var intersections = SequenceOperations.findIntersections(sequences);

            for (int idx = 0; idx < intersections.size(); idx++) {
                if (span.containsRange(intersections.getInt(idx), sequences.length)) {
                    return true;
                }
            }

            return false;
        }

    }
}

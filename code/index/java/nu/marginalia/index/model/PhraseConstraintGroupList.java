package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.language.keywords.KeywordHasher;
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
    /** A list of groups representing parts of the query that must be present in the specified order */
    private final List<PhraseConstraintGroup> mandatoryGroups = new ArrayList<>();

    /** A list of groups representing segments of the query */
    private final List<PhraseConstraintGroup> optionalGroups = new ArrayList<>();

    /** A group representing all terms in the query, segmentation be damned */
    private final PhraseConstraintGroup fullGroup;

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

    public static final class PhraseConstraintGroup {
        private final int[] offsets;
        private final BitSet present;
        private final BitSet termIdsMask;
        private final int presentCardinality;

        public final int size;
        public PhraseConstraintGroup(KeywordHasher hasher, List<String> terms, TermIdList termIdsAll) {
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
                long termId = hasher.hashKeyword(term);

                int idx = termIdsAll.indexOf(termId);
                if (idx < 0) {
                    offsets[i++] = -1;
                }
                else {
                    offsets[i++] = idx;
                    termIdsMask.set(idx);
                }
            }

            presentCardinality = present.cardinality();
        }

        /** Returns true if the term with index termIdx in the query is in the group */
        public boolean containsTerm(int termIdx) {
            return termIdsMask.get(termIdx);
        }

        public boolean test(CodedSequence[] positions) {
            IntIterator[] sequences = new IntIterator[presentCardinality];

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


        public IntList findIntersections(IntList[] positions) {
            IntList[] sequences = new IntList[presentCardinality];
            int[] iterOffsets = new int[sequences.length];

            for (int oi = 0, si = 0; oi < offsets.length; oi++) {
                if (!present.get(oi)) {
                    continue;
                }
                int offset = offsets[oi];
                if (offset < 0)
                    return IntList.of();

                // Create iterators that are offset by their relative position in the
                // sequence.  This is done by subtracting the index from the offset,
                // so that when we intersect them, an overlap means that the terms are
                // in the correct order.  Note the offset is negative!

                var posForTerm = positions[offset];
                if (posForTerm == null) {
                    return IntList.of();
                }
                sequences[si++] = posForTerm;
                iterOffsets[si - 1] = -oi;
            }

            return SequenceOperations.findIntersections(sequences, iterOffsets, Integer.MAX_VALUE);
        }


        public IntList findIntersections(IntList[] positions, int n) {
            IntList[] sequences = new IntList[presentCardinality];
            int[] iterOffsets = new int[sequences.length];

            for (int oi = 0, si = 0; oi < offsets.length; oi++) {
                if (!present.get(oi)) {
                    continue;
                }
                int offset = offsets[oi];
                if (offset < 0)
                    return IntList.of();

                // Create iterators that are offset by their relative position in the
                // sequence.  This is done by subtracting the index from the offset,
                // so that when we intersect them, an overlap means that the terms are
                // in the correct order.  Note the offset is negative!

                var posForTerm = positions[offset];
                if (posForTerm == null) {
                    return IntList.of();
                }
                sequences[si++] = posForTerm;
                iterOffsets[si - 1] = -oi;
            }

            return SequenceOperations.findIntersections(sequences, iterOffsets, n);
        }

        public int minDistance(IntList[] positions) {
            List<IntList> sequences = new ArrayList<>(presentCardinality);
            IntList iterOffsets = new IntArrayList(presentCardinality);

            for (int oi = 0; oi < offsets.length; oi++) {
                if (!present.get(oi)) {
                    continue;
                }
                int offset = offsets[oi];
                if (offset < 0)
                    return Integer.MAX_VALUE;

                // Create iterators that are offset by their relative position in the
                // sequence.  This is done by subtracting the index from the offset,
                // so that when we intersect them, an overlap means that the terms are
                // in the correct order.  Note the offset is negative!

                var posForTerm = positions[offset];
                if (posForTerm == null) {
                    return Integer.MAX_VALUE;
                }

                if (posForTerm.size() > 16) { // heuristic to avoid large sequences, which is expensive and not very useful
                    continue;
                }

                sequences.add(posForTerm);
                iterOffsets.add(-oi);
            }

            return SequenceOperations.minDistance(sequences.toArray(IntList[]::new), iterOffsets.toIntArray());
        }
    }
}

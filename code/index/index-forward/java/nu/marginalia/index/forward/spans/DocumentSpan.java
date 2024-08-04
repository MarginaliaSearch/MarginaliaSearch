package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.sequence.SequenceOperations;

public class DocumentSpan {

    /** A list of the interlaced start and end positions of each span in the document of this type */
    private final CodedSequence startsEnds;

    public DocumentSpan(CodedSequence startsEnds) {
        this.startsEnds = startsEnds;
    }

    public DocumentSpan() {
        this.startsEnds = null;
    }

    public boolean containsPosition(int position) {
        if (startsEnds == null) {
            return false;
        }

        var iter = startsEnds.iterator();
        while (iter.hasNext()) {
            int start = iter.nextInt();
            if (start > position) {
                return false;
            }
            int end = iter.nextInt();
            if (end > position) {
                return true;
            }
        }

        return false;
    }

    public boolean containsRange(int rangeStart, int len) {
        if (startsEnds == null) {
            return false;
        }

        var iter = startsEnds.iterator();
        while (iter.hasNext()) {
            int start = iter.nextInt();
            if (start > rangeStart) {
                return false;
            }
            int end = iter.nextInt();
            if (end > rangeStart + len) {
                return true;
            }
        }

        return false;
    }

    public boolean overlapsRange(CodedSequence sequence) {
        return SequenceOperations.intersectSequences(iterator(), sequence.iterator());
    }

    /** Returns an iterator over the start and end positions of each span in the document of this type */
    public IntIterator iterator() {
        if (null == startsEnds) {
            return IntList.of().iterator();
        }

        return startsEnds.iterator();
    }

    public int length() {
        if (null == startsEnds) {
            return 0;
        }

        int len = 0;
        var iter = startsEnds.iterator();

        while (iter.hasNext()) {
            len -= iter.nextInt();
            len += iter.nextInt();
        }

        return len;
    }

    public int size() {
        if (null == startsEnds) {
            return 0;
        }

        return startsEnds.valueCount() / 2;
    }
}

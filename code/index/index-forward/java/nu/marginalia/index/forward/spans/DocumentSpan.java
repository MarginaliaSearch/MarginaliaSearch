package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.sequence.CodedSequence;

public class DocumentSpan {

    /** A list of the interlaced start and end positions of each span in the document of this type */
    private final CodedSequence startsEnds;

    public DocumentSpan(CodedSequence startsEnds) {
        this.startsEnds = startsEnds;
    }

    public DocumentSpan() {
        this.startsEnds = null;
    }

    public boolean intersects(IntIterator positionsIter) {
        if (null == startsEnds || !positionsIter.hasNext()) {
            return false;
        }

        var iter = startsEnds.iterator();
        int start = -1;
        int end = -1;

        while (iter.hasNext() && positionsIter.hasNext()) {
            if (start < 0) {
                start = iter.nextInt();
                end = iter.nextInt();
            }

            int position = positionsIter.nextInt();
            if (position < start) {
                continue;
            }

            if (position < end) {
                return true;
            }

            start = -1;
        }

        return false;
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

    /** Returns an iterator over each position between the start and end positions of each span in the document of this type */
    public IntIterator iterator() {
        if (null == startsEnds) {
            return IntList.of().iterator();
        }

        return new DocumentSpanPositionsIterator();
    }

    /** Iteator over the values between the start and end positions of each span in the document of this type */
    class DocumentSpanPositionsIterator implements IntIterator {
        private final IntIterator startStopIterator;

        private int value = -1;
        private int current = -1;
        private int end = -1;

        public DocumentSpanPositionsIterator() {
            this.startStopIterator = startsEnds.iterator();
        }

        @Override
        public int nextInt() {
            if (hasNext()) {
                int ret = value;
                value = -1;
                return ret;
            }
            throw new IllegalStateException();
        }

        @Override
        public boolean hasNext() {
            if (value >= 0) {
                return true;
            }
            else if (current >= 0 && current < end) {
                value = ++current;
                return true;
            }
            else if (startStopIterator.hasNext()) {
                current = startStopIterator.nextInt();
                end = startStopIterator.nextInt();
                value = current;
                return true;
            }

            return false;
        }
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

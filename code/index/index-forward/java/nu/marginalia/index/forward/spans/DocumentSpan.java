package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.sequence.CodedSequence;

public class DocumentSpan {

    /** A list of the interlaced start and end positions of each span in the document of this type */
    private final IntList startsEnds;

    public DocumentSpan(CodedSequence startsEnds) {
        this.startsEnds = startsEnds.values();
    }

    public DocumentSpan() {
        this.startsEnds = null;
    }

    public int countIntersections(IntList positions) {
        if (null == startsEnds || startsEnds.isEmpty() || positions.isEmpty()) {
            return 0;
        }

        int cnt = 0;
        int seis = 0;

        for (int pi = 0; pi < positions.size(); pi++) {
            int position = positions.getInt(pi);

            for (int sei = seis; sei < startsEnds.size(); sei ++) {
                if (startsEnds.getInt(sei) > position) {
                    cnt += sei % 2;
                    seis = Math.max(seis, sei - 1);
                    break;
                }
            }
        }

        return cnt;
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

    public boolean containsRange(IntList positions, int len) {
        if (null == startsEnds || startsEnds.size() < 2 || positions.isEmpty()) {
            return false;
        }

        int sei = 0;


        int start = startsEnds.getInt(sei++);
        int end = startsEnds.getInt(sei++);

        for (int pi = 0; pi < positions.size(); pi++) {
            int position = positions.getInt(pi);
            if (position < start) {
                continue;
            }

            if (position + len < end) {
                return true;
            } else if (sei + 2 < startsEnds.size()) {
                start = startsEnds.getInt(sei++);
                end = startsEnds.getInt(sei++);
            }
            else {
                return false;
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

        return startsEnds.size() / 2;
    }
}

package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.sequence.CodedSequence;

/** A list of the interlaced start and end positions of each span in the document of this type */
public class DocumentSpan {

    /** A list of the interlaced start and end positions of each span in the document of this type */
    private final IntList startsEnds;

    public DocumentSpan(IntList startsEnds) {
        this.startsEnds = startsEnds;
    }
    public DocumentSpan(CodedSequence startsEnds) {
        this.startsEnds = startsEnds.values();
    }

    public DocumentSpan() {
        this.startsEnds = null;
    }

    public int countIntersections(IntList positions) {
        if (null == startsEnds || startsEnds.isEmpty() || positions.size() == 0) {
            return 0;
        }

        int sei = 0;
        int pi = 0;
        int start = startsEnds.getInt(sei++);
        int end = startsEnds.getInt(sei++);
        int pos = -1;

        int cnt = 0;
        while (pi < positions.size() && sei < startsEnds.size()) {
            if (pos < start) {
                pos = positions.getInt(pi++);
            }
            else if (pos < end) {
                cnt++;
                pos = positions.getInt(pi++);
            }
            else {
                start = startsEnds.getInt(sei++);
                end = startsEnds.getInt(sei++);
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

    /** Returns true if for any position in the list, there exists a range
     * (position[i], position[i]+len] that is overlapped by a span */
    public boolean containsRange(IntList positions, int len) {
        if (null == startsEnds || startsEnds.size() < 2 || positions.isEmpty()) {
            return false;
        }

        int sei = 0;


        int start = startsEnds.getInt(sei++);
        int end = startsEnds.getInt(sei++);

        for (int pi = 0; pi < positions.size();) {
            int position = positions.getInt(pi);
            if (position >= start && position + len <= end) {
                return true;
            }
            else if (position < end) {
                pi++;
            } else if (sei + 2 <= startsEnds.size()) {
                start = startsEnds.getInt(sei++);
                end = startsEnds.getInt(sei++);
            }
            else {
                return false;
            }

        }

        return false;
    }

    /** Returns the number of instances there exists a range
     * (position[i], position[i]+len] that is overlapped by a span */
    public int countRangeMatchesExact(IntList positions, int len) {
        if (null == startsEnds || startsEnds.size() < 2 || positions.isEmpty()) {
            return 0;
        }

        int sei = 0;
        int cnt = 0;

        int start = startsEnds.getInt(sei++);
        int end = startsEnds.getInt(sei++);

        for (int pi = 0; pi < positions.size(); ) {
            int position = positions.getInt(pi);

            if (position == start && position + len == end) {
                cnt++;
                if (sei + 2 <= startsEnds.size()) {
                    pi = 0;
                    start = startsEnds.getInt(sei++);
                    end = startsEnds.getInt(sei++);
                }
                else {
                    break;
                }
            }
            else if (position < end) {
                pi++;
            } else if (sei + 2 <= startsEnds.size()) {
                start = startsEnds.getInt(sei++);
                end = startsEnds.getInt(sei++);
            }
            else {
                break;
            }
        }

        return cnt;
    }

    public int countRangeMatches(IntList positions, int len) {
        if (null == startsEnds || startsEnds.size() < 2 || positions.isEmpty()) {
            return 0;
        }

        int sei = 0;
        int ret = 0;

        int start = startsEnds.getInt(sei++);
        int end = startsEnds.getInt(sei++);

        for (int pi = 0; pi < positions.size();) {
            int position = positions.getInt(pi);
            if (position >= start && position + len <= end) {
                ret++;
                pi++;
            }
            else if (position < end) {
                pi++;
            }
            else if (sei + 2 <= startsEnds.size()) {
                start = startsEnds.getInt(sei++);
                end = startsEnds.getInt(sei++);
            }
            else {
                return ret;
            }

        }

        return ret;
    }

    /** Returns an iterator over each position between the start and end positions of each span in the document of this type */
    public IntIterator iterator() {
        if (null == startsEnds) {
            return IntList.of().iterator();
        }

        return new DocumentSpanPositionsIterator();
    }

    /** Returns a list with all values between the start and end positions of each span in the document of this type
     * This is an expensive operation and should not be used in the main execution path, but only for debugging
     * and testing
     * */
    public IntList positionValues() {
        if (null == startsEnds)
            return IntList.of();

        IntList ret = new IntArrayList();
        var iter = startsEnds.iterator();

        while (iter.hasNext()) {
            int start = iter.nextInt();
            int end = iter.nextInt();
            for (int i = start; i < end; i++) {
                ret.add(i);
            }
        }

        return ret;
    }

    /** Iteator over the values between the start and end positions of each span in the document of this type
     * */
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
            // The length of each span is b - a; but we receive them in the order a b;
            // thus we subtract the start from the length and add the end
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

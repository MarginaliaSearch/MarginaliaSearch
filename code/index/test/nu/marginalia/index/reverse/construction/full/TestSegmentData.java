package nu.marginalia.index.reverse.construction.full;

import java.util.Arrays;

record TestSegmentData(String wordId, long start, long end, long[] data) {
    public TestSegmentData(String wordId, long start, long end) {
        this(wordId, start, end, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TestSegmentData that = (TestSegmentData) o;

        if (wordId != that.wordId) return false;
        if (start != that.start) return false;
        if (end != that.end) return false;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = wordId.hashCode();
        result = 31 * result + (int) (start ^ (start >>> 32));
        result = 31 * result + (int) (end ^ (end >>> 32));
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "TestSegmentData{" +
                "wordId=" + wordId +
                ", start=" + start +
                ", end=" + end +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}

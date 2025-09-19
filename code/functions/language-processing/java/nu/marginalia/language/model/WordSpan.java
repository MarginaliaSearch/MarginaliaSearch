package nu.marginalia.language.model;

import org.jetbrains.annotations.NotNull;

public class WordSpan implements Comparable<WordSpan> {
    public final int start;
    public final int end;

    public WordSpan(int start, int end) {
        assert end >= start;

        this.start = start;
        this.end = end;
    }

    public int size() {
        return end - start;
    }

    @Override
    public int compareTo(@NotNull WordSpan o) {
        return start - o.start;
    }

    public boolean overlaps(WordSpan other) {
        if (other.start >= start && other.start <= end) return true;
        if (other.end >= start && other.end <= end) return true;
        if (start >= other.start && start <= other.end) return true;
        return false;
    }

    public int distance(WordSpan other) {
        if (overlaps(other)) {
            return 0;
        }
        if (start < other.start) {
            return end - other.start;
        } else {
            return other.end - start;
        }

    }

    public String toString() {
        return String.format("WordSpan[%s,%s]", start, end);
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof WordSpan)) return false;
        final WordSpan other = (WordSpan) o;
        if (!other.canEqual((Object) this)) return false;
        if (this.start != other.start) return false;
        if (this.end != other.end) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof WordSpan;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.start;
        result = result * PRIME + this.end;
        return result;
    }
}
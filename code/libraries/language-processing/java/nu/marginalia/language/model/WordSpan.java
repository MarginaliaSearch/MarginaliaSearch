package nu.marginalia.language.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor @EqualsAndHashCode
public  class WordSpan implements Comparable<WordSpan>{
    public final int start;
    public final int end;

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
        }
        else {
            return other.end - start;
        }

    }

    public String toString() {
        return String.format("WordSpan[%s,%s]", start, end);
    }
}
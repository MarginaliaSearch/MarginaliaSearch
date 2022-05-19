package nu.marginalia.wmsa.memex.model;

import lombok.EqualsAndHashCode;

import java.util.Arrays;
import java.util.stream.Collectors;

@EqualsAndHashCode
public class MemexNodeHeadingId implements Comparable<MemexNodeHeadingId> {
    private final int[] ids;

    public static final MemexNodeHeadingId ROOT = new MemexNodeHeadingId(0);

    public MemexNodeHeadingId(int... ids) {
        this.ids = ids;
    }

    public static MemexNodeHeadingId parse(String section) {
        return new MemexNodeHeadingId(Arrays.stream(section.split("\\.")).mapToInt(Integer::parseInt).toArray());
    }

    public int getLevel() {
        return ids.length;
    }

    public int[] getIds() {
        return ids;
    }
    public boolean isChildOf(MemexNodeHeadingId other) {
        if (other.equals(ROOT)) {
            return true;
        }
        if (other.ids.length > ids.length) {
            return false;
        }

        for (int i = 0; i < other.ids.length; i++) {
            if (other.ids[i] != ids[i]) {
                return false;
            }
        }

        return true;
    }

    // This does not have the same semantics as Arrays$compare

    public int compareTo(MemexNodeHeadingId other) {
        for (int i = 0; i < Math.min(ids.length, other.ids.length); i++) {
            if (other.ids[i] != ids[i]) {
                return ids[i] - other.ids[i];
            }
        }

        return other.ids.length - ids.length;
    }

    public MemexNodeHeadingId parent() {
        if (ids.length <= 1)
            return ROOT;
        else return new MemexNodeHeadingId(Arrays.copyOfRange(ids, 0, ids.length-1));

    }
    public MemexNodeHeadingId next(int level) {
        int[] newIds = Arrays.copyOf(ids, level+1);
        newIds[level]++;
        return new MemexNodeHeadingId(newIds);
    }

    @Override
    public String toString() {
        return Arrays.stream(ids).mapToObj(Integer::toString).collect(Collectors.joining("."));
    }
}

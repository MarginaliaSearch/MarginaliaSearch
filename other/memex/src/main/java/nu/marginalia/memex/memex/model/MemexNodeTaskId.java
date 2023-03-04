package nu.marginalia.memex.memex.model;

import lombok.EqualsAndHashCode;

import java.util.Arrays;
import java.util.stream.Collectors;

@EqualsAndHashCode
public class MemexNodeTaskId implements Comparable<MemexNodeTaskId> {
    private final int[] ids;

    public MemexNodeTaskId(int... ids) {
        this.ids = ids;
    }

    public static MemexNodeTaskId parse(String section) {
        return new MemexNodeTaskId(Arrays.stream(section.split("\\.")).mapToInt(Integer::parseInt).toArray());
    }

    public int level() {
        return ids.length;
    }

    public boolean isChildOf(MemexNodeTaskId other) {
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

    public int compareTo(MemexNodeTaskId other) {
        for (int i = 0; i < Math.min(ids.length, other.ids.length); i++) {
            if (other.ids[i] != ids[i]) {
                return ids[i] - other.ids[i];
            }
        }

        return other.ids.length - ids.length;
    }

    public MemexNodeTaskId parent() {
        if (ids.length <= 1)
            return new MemexNodeTaskId(0);
        else return new MemexNodeTaskId(Arrays.copyOfRange(ids, 0, ids.length-1));

    }
    public MemexNodeTaskId next(int level) {
        int[] newIds = Arrays.copyOf(ids, level+1);
        newIds[level]++;
        return new MemexNodeTaskId(newIds);
    }

    @Override
    public String toString() {
        return Arrays.stream(ids).mapToObj(Integer::toString).collect(Collectors.joining("."));
    }
}

package nu.marginalia.wmsa.edge.model.id;

import java.util.Arrays;
import java.util.stream.IntStream;

public interface EdgeIdCollection<T> {
    int size();
    boolean isEmpty();
    int[] values();

    default IntStream stream() {
        return Arrays.stream(values());
    }

    default EdgeIdArray<T> asArray() {
        return new EdgeIdArray<>(values());
    }
    default EdgeIdList<T> asList() {
        return new EdgeIdList<>(values());
    }
    default EdgeIdSet<T> asSet() {
        return new EdgeIdSet<>(values());
    }
}

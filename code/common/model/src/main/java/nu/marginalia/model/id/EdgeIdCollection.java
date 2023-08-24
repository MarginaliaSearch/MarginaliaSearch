package nu.marginalia.model.id;

import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.IntStream;

@Deprecated
public interface EdgeIdCollection<T> extends Iterable<EdgeId<T>> {
    int size();
    boolean isEmpty();
    int[] values();

    default IntStream stream() {
        return Arrays.stream(values());
    }

    default Iterator<EdgeId<T>> iterator() {
        return Arrays.stream(values()).mapToObj(EdgeId<T>::new).iterator();
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

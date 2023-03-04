package nu.marginalia.model.id;

import java.util.Arrays;
import java.util.stream.IntStream;

public record EdgeIdArray<T> (int... values) implements EdgeIdCollection<T> {

    public static <T> EdgeIdArray<T> gather(IntStream stream) {
        return new EdgeIdArray<>(stream.toArray());
    }

    @Override
    public int[] values() {
        return values;
    }

    @Override
    public boolean isEmpty() {
        return values.length == 0;
    }

    @Override
    public int size() {
        return values.length;
    }

    public int get(int idx) {
        return values[idx];
    }

    public void sort() {
        Arrays.sort(values);
    }
}
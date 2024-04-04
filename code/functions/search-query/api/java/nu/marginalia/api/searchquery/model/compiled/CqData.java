package nu.marginalia.api.searchquery.model.compiled;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

public class CqData<T> {
    private final T[] data;

    public CqData(T[] data) {
        this.data = data;
    }

    @SuppressWarnings("unchecked")
    public <T2> CqData<T2> map(Class<T2> clazz, Function<T, T2> mapper) {
        T2[] newData = (T2[]) Array.newInstance(clazz, data.length);
        for (int i = 0; i < data.length; i++) {
            newData[i] = mapper.apply((T) data[i]);
        }

        return new CqData<>(newData);
    }

    public CqDataLong mapToLong(ToLongFunction<T> mapper) {
        long[] newData = new long[data.length];
        for (int i = 0; i < data.length; i++) {
            newData[i] = mapper.applyAsLong((T) data[i]);
        }

        return new CqDataLong(newData);
    }

    public T get(int i) {
        return data[i];
    }

    public T get(CqExpression.Word w) {
        return data[w.idx()];
    }

    public Stream<T> stream() {
        return Arrays.stream(data);
    }

    public int size() {
        return data.length;
    }
}

package nu.marginalia.api.searchquery.model.compiled;

import java.util.Arrays;
import java.util.stream.IntStream;

public class CqDataInt {
    private final int[] data;

    public CqDataInt(int[] data) {
        this.data = data;
    }

    public int get(int i) {
        return data[i];
    }
    public int get(CqExpression.Word w) {
        return data[w.idx()];
    }

    public IntStream stream() {
        return Arrays.stream(data);
    }

    public int size() {
        return data.length;
    }

    public int[] copyData() {
        return Arrays.copyOf(data, data.length);
    }
}

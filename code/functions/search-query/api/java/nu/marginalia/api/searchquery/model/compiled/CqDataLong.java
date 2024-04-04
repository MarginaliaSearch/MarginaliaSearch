package nu.marginalia.api.searchquery.model.compiled;

import java.util.Arrays;
import java.util.stream.LongStream;

public class CqDataLong {
    private final long[] data;

    public CqDataLong(long[] data) {
        this.data = data;
    }

    public long get(int i) {
        return data[i];
    }
    public long get(CqExpression.Word w) {
        return data[w.idx()];
    }

    public LongStream stream() {
        return Arrays.stream(data);
    }

    public int size() {
        return data.length;
    }
}

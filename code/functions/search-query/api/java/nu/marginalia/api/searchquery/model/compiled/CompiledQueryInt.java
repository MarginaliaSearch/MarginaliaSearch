package nu.marginalia.api.searchquery.model.compiled;

import java.util.stream.IntStream;


/** A compiled index service query */
public class CompiledQueryInt {
    public final CqExpression root;
    public final CqDataInt data;

    public CompiledQueryInt(CqExpression root, CqDataInt data) {
        this.root = root;
        this.data = data;
    }


    public CqExpression root() {
        return root;
    }

    public IntStream stream() {
        return data.stream();
    }

    public IntStream indices() {
        return IntStream.range(0, data.size());
    }

    public int at(int index) {
        return data.get(index);
    }

    public int[] copyData() {
        return data.copyData();
    }

    public boolean isEmpty() {
        return data.size() == 0;
    }

    public int size() {
        return data.size();
    }
}

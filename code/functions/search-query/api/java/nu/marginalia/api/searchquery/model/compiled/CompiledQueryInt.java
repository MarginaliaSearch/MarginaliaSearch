package nu.marginalia.api.searchquery.model.compiled;

import java.util.stream.IntStream;


/** A compiled index service query */
public class CompiledQueryInt extends CompiledQueryTopology {
    public final CqDataInt data;

    public CompiledQueryInt(CompiledQueryTopology topology, CqDataInt data) {
        super(topology);
        this.data = data;
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

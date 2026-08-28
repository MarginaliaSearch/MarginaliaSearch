package nu.marginalia.api.searchquery.model.compiled;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;


/** A compiled index service query */
public class CompiledQueryLong extends CompiledQueryTopology implements Iterable<Long> {

    public final CqDataLong data;

    public CompiledQueryLong(CompiledQueryTopology topology, CqDataLong data) {
        super(topology);
        this.data = data;
    }

    public LongStream stream() {
        return data.stream();
    }

    public IntStream indices() {
        return IntStream.range(0, data.size());
    }

    public long at(int index) {
        return data.get(index);
    }

    @NotNull
    @Override
    public Iterator<Long> iterator() {
        return stream().iterator();
    }

    public long[] copyData() {
        return data.copyData();
    }

    public boolean isEmpty() {
        return data.size() == 0;
    }

    public int size() {
        return data.size();
    }
}

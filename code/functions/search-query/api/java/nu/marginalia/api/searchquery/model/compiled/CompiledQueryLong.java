package nu.marginalia.api.searchquery.model.compiled;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;


/** A compiled index service query */
public class CompiledQueryLong implements Iterable<Long> {
    private final CqExpression root;
    private final CqDataLong data;

    public CompiledQueryLong(CqExpression root, CqDataLong data) {
        this.root = root;
        this.data = data;
    }


    public CqExpression root() {
        return root;
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
}

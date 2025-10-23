package nu.marginalia.api.searchquery.model.compiled;

import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;


/** A compiled index service query */
public class CompiledQueryLong extends CompiledQueryTopology implements Iterable<Long> {

    public final CqDataLong data;

    public CompiledQueryLong(CqExpression root, List<IntList> paths, CqDataLong data) {
        super(root, paths);
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

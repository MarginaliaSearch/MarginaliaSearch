package nu.marginalia.api.searchquery.model.compiled;

import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/** A compiled index service query.  The class separates the topology of the query from the data,
 * and it's possible to create new queries supplanting the data */
public class CompiledQuery<T>  extends CompiledQueryTopology implements Iterable<T> {

    /** The root expression, conveys the topology of the query */
    public final CqData<T> data;

    public CompiledQuery(CqExpression root, List<IntList> paths, CqData<T> data) {
        super(root, paths);
        this.data = data;
    }

    public CompiledQuery(CqExpression root, List<IntList> paths, T[] data) {
        super(root, paths);
        this.data = new CqData<>(data);
    }

    /** Exists for testing, creates a simple query that ANDs all the provided items */
    public static <T> CompiledQuery<T> just(T... item) {
        var expr = new CqExpression.And(
                IntStream.range(0, item.length).mapToObj(CqExpression.Word::new).toList()
        );

        return new CompiledQuery<>(expr, CqExpression.allPaths(expr), item);
    }

    /** Create a new CompiledQuery mapping the leaf nodes using the provided mapper */
    public <T2> CompiledQuery<T2> map(Class<T2> clazz, Function<T, T2> mapper) {
        return new CompiledQuery<>(
                root,
                paths,
                data.map(clazz, mapper));
    }

    public CompiledQueryLong mapToLong(ToLongFunction<T> mapper) {
        return new CompiledQueryLong(root, paths, data.mapToLong(mapper));
    }

    public CompiledQueryInt mapToInt(ToIntFunction<T> mapper) {
        return new CompiledQueryInt(root, paths, data.mapToInt(mapper));
    }

    public CompiledQueryLong forData(long[] newData) {
        return new CompiledQueryLong(root, paths, new CqDataLong(newData));
    }

    public <T> CompiledQuery<T> forData(T[] newData) {
        return new CompiledQuery<T>(root, paths, new CqData<T>(newData));
    }

    public CqExpression root() {
        return root;
    }

    public Stream<T> stream() {
        return data.stream();
    }

    public IntStream indices() {
        return IntStream.range(0, data.size());
    }

    public T at(int index) {
        return data.get(index);
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return stream().iterator();
    }

    public int size() {
        return data.size();
    }


}

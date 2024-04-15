package nu.marginalia.api.searchquery.model.compiled;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/** A compiled index service query.  The class separates the topology of the query from the data,
 * and it's possible to create new queries supplanting the data */
public class CompiledQuery<T> implements Iterable<T> {

    /** The root expression, conveys the topology of the query */
    public final CqExpression root;

    private final CqData<T> data;

    public CompiledQuery(CqExpression root, CqData<T> data) {
        this.root = root;
        this.data = data;
    }

    public CompiledQuery(CqExpression root, T[] data) {
        this.root = root;
        this.data = new CqData<>(data);
    }

    /** Exists for testing, creates a simple query that ANDs all the provided items */
    public static <T> CompiledQuery<T> just(T... item) {
        return new CompiledQuery<>(new CqExpression.And(
                IntStream.range(0, item.length).mapToObj(CqExpression.Word::new).toList()
        ), item);
    }

    /** Create a new CompiledQuery mapping the leaf nodes using the provided mapper */
    public <T2> CompiledQuery<T2> map(Class<T2> clazz, Function<T, T2> mapper) {
        return new CompiledQuery<>(
                root,
                data.map(clazz, mapper)
        );
    }

    public CompiledQueryLong mapToLong(ToLongFunction<T> mapper) {
        return new CompiledQueryLong(root, data.mapToLong(mapper));
    }

    public CompiledQueryLong mapToInt(ToIntFunction<T> mapper) {
        return new CompiledQueryLong(root, data.mapToInt(mapper));
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

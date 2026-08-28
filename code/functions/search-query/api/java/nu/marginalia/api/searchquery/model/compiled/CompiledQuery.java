package nu.marginalia.api.searchquery.model.compiled;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/** A compiled index service query.  The class separates the topology of the query from the data,
 * and it's possible to create new queries supplanting the data */
public class CompiledQuery<T> extends CompiledQueryTopology implements Iterable<T> {

    public final CqData<T> data;

    /** Create a new CompiledQuery
     *
     * @param paths  Each path is a sorted list of distinct term indices.
     * @param variantClasses  Maps an alternative term to its original variant term index
     * @param terms  The terms that make up the query
     */
    public CompiledQuery(List<IntList> paths,
                         int[] variantClasses,
                         T[] terms)
    {
        super(paths, variantClasses);
        this.data = new CqData<>(terms);

        if (variantClasses.length != data.size()) {
            throw new IllegalArgumentException("Variant classes do not match the term table");
        }

        for (int idx : variantClasses) {
            if (idx < 0 || idx >= data.size()) {
                throw new IllegalArgumentException("Term index " + idx + " is outside of the term table");
            }
        }

        for (IntList path : paths) {
            for (int idx : path) {
                if (idx < 0 || idx >= data.size()) {
                    throw new IllegalArgumentException("Term index " + idx + " is outside of the term table");
                }
            }
        }
    }

    /** Create a query with the topology of another query but different data */
    public CompiledQuery(CompiledQueryTopology topology, CqData<T> data) {
        super(topology);
        this.data = data;
    }

    /** For testing */
    @SafeVarargs
    public static <T> CompiledQuery<T> just(T... item) {
        int[] variantClasses = new int[item.length];
        IntList path = new IntArrayList(item.length);

        for (int i = 0; i < item.length; i++) {
            variantClasses[i] = i;
            path.add(i);
        }

        return new CompiledQuery<>(List.of(path), variantClasses, item);
    }
    /** Create a new CompiledQuery mapping the leaf nodes using the provided mapper */
    public <T2> CompiledQuery<T2> map(Class<T2> clazz, Function<T, T2> mapper) {
        return new CompiledQuery<>(this, data.map(clazz, mapper));
    }

    public CompiledQueryLong mapToLong(ToLongFunction<T> mapper) {
        return new CompiledQueryLong(this, data.mapToLong(mapper));
    }

    public CompiledQueryInt mapToInt(ToIntFunction<T> mapper) {
        return new CompiledQueryInt(this, data.mapToInt(mapper));
    }

    public CompiledQueryLong forData(long[] newData) {
        return new CompiledQueryLong(this, new CqDataLong(newData));
    }

    public <T2> CompiledQuery<T2> forData(T2[] newData) {
        return new CompiledQuery<>(this, new CqData<>(newData));
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

    public boolean isEmpty() {
        return data.size() == 0;
    }

    public int size() {
        return data.size();
    }

    @Override
    public String toString() {
        StringJoiner pathsJoiner = new StringJoiner(" ");
        for (IntList path : paths) {
            StringJoiner termsJoiner = new StringJoiner(" ", "[", "]");
            for (int idx : path) {
                termsJoiner.add(String.valueOf(data.get(idx)));
            }
            pathsJoiner.add(termsJoiner.toString());
        }
        return pathsJoiner.toString();
    }
}

package nu.marginalia.model.id;

import gnu.trove.TIntCollection;
import gnu.trove.set.hash.TIntHashSet;

import java.util.stream.IntStream;

@Deprecated
public record EdgeIdSet<T> (TIntHashSet set) implements EdgeIdCollection<T>, EdgeIdCollectionMutable<T> {

    public EdgeIdSet(int... values) {
        this(new TIntHashSet(values.length, 0.5f, -1));

        set.addAll(values);
    }

    public EdgeIdSet(int initialCapacity, float loadFactor) {
        this(new TIntHashSet(initialCapacity, loadFactor, -1));
    }

    @Override
    public TIntCollection underlyingCollection() {
        return set;
    }

    public static <T> EdgeIdSet<T> gather(IntStream stream) {
        return new EdgeIdSet<>(stream.toArray());
    }

    @Override
    public int[] values() {
        return set.toArray();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public int size() {
        return set.size();
    }

    public boolean contains(int id) {
        return set.contains(id);
    }
    public boolean add(int id) {
        return set.add(id);
    }
    public boolean remove(int id) { return set.remove(id); }

}
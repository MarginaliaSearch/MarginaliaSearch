package nu.marginalia.model.id;

import gnu.trove.TIntCollection;
import gnu.trove.list.array.TIntArrayList;

import java.util.stream.IntStream;

@Deprecated
public record EdgeIdList<T> (TIntArrayList list) implements
        EdgeIdCollection<T>,
        EdgeIdCollectionMutable<T> {

    public EdgeIdList(int... values) { this(new TIntArrayList(values)); }
    public static <T> EdgeIdList<T> gather(IntStream stream) {
        return stream.collect(EdgeIdList::new, EdgeIdList::add, EdgeIdList::addAll);
    }

    @Override
    public int[] values() {
        return list.toArray();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public int size() {
        return list.size();
    }

    public int get(int idx) {
        return list.get(idx);
    }

    public void add(int id) {
        list.add(id);
    }

    public void sort() {
        list.sort();
    }

    @Override
    public TIntCollection underlyingCollection() {
        return list;
    }
}
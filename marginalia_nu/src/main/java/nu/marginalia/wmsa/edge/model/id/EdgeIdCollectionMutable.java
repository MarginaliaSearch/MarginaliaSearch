package nu.marginalia.wmsa.edge.model.id;

import gnu.trove.TIntCollection;

public interface EdgeIdCollectionMutable<T> {
    TIntCollection underlyingCollection();

    default void addAll(EdgeIdArray<T> other) { underlyingCollection().addAll(other.values()); }
    default void addAll(EdgeIdList<T> other) { underlyingCollection().addAll(other.list()); }
    default void addAll(EdgeIdCollection<T> other) { underlyingCollection().addAll(other.values()); }

}

package nu.marginalia.model.id;

import gnu.trove.TIntCollection;

@Deprecated
public interface EdgeIdCollectionMutable<T> {
    TIntCollection underlyingCollection();

    default void addAll(EdgeIdArray<T> other) { underlyingCollection().addAll(other.values()); }
    default void addAll(EdgeIdList<T> other) { underlyingCollection().addAll(other.list()); }
    default void addAll(EdgeIdCollection<T> other) { underlyingCollection().addAll(other.values()); }

}

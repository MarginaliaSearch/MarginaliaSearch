package nu.marginalia.index;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** A priority queue for search results. This class is not thread-safe,
 * in general, except for concurrent use of the addAll method.
 * <p></p>
 * The class implements a subset of the Collection interface, and
 * is intended to be used as a priority queue for search results,
 * with a maximum size.
 * <p></p>
 * Since the expected use case is to add a large number of items
 * and then iterate over the items, the class is optimized for
 * this scenario, and does not implement other mutating methods
 * than addAll().
 */
public class ResultPriorityQueue implements Iterable<SearchResultItem>,
        Collection<SearchResultItem> {
    private final int limit;
    private final ArrayList<SearchResultItem> backingList = new ArrayList<>();
    private final LongOpenHashSet idsInSet = new LongOpenHashSet();

    public ResultPriorityQueue(int limit) {
        this.limit = limit;
    }

    public Iterator<SearchResultItem> iterator() {
        return backingList.iterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return backingList.toArray();
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        return backingList.toArray(a);
    }

    @Override
    public boolean add(SearchResultItem searchResultItem) {
        throw new UnsupportedOperationException("Use addAll instead");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return idsInSet.containsAll(c);
    }

    /** Adds all items to the queue, and returns true if any items were added.
     * This is a thread-safe operation.
     */
    @Override
    public synchronized boolean addAll(@NotNull Collection<? extends SearchResultItem> items) {
        boolean itemsAdded = false;
        for (var item: items) {
            if (idsInSet.add(item.getDocumentId())) {
                backingList.add(item);
                itemsAdded = true;
            }
        }
        if (!itemsAdded) {
            return false;
        }

        backingList.sort(Comparator.naturalOrder());
        if (backingList.size() > limit) {
            backingList.subList(limit, backingList.size()).clear();
        }

        return true;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        backingList.clear();
        idsInSet.clear();
    }

    public int size() {
        return backingList.size();
    }

    @Override
    public boolean isEmpty() {
        return backingList.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return backingList.contains(o);
    }

}

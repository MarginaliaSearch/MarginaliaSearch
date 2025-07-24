package nu.marginalia.index;

import com.google.common.collect.MinMaxPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;

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
    private final LongOpenHashSet idsInSet = new LongOpenHashSet();
    private final MinMaxPriorityQueue<SearchResultItem> queue;

    public ResultPriorityQueue(int limit) {
        this.queue = MinMaxPriorityQueue.<SearchResultItem>orderedBy(Comparator.naturalOrder()).maximumSize(limit).create();
    }

    public Iterator<SearchResultItem> iterator() {
        return queue.iterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return queue.toArray();
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        return queue.toArray(a);
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

        for (var item : items) {
            if (idsInSet.add(item.getDocumentId())) {
                queue.add(item);
            }
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
        queue.clear();
        idsInSet.clear();
    }

    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return queue.contains(o);
    }

}

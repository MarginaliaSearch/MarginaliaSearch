package nu.marginalia.index;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
        throw new UnsupportedOperationException("Use addAll instead ya dingus");
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof SearchResultItem sri) {
            idsInSet.remove(sri.getDocumentId());
            return idsInSet.remove(sri.getDocumentId());
        }
        throw new IllegalArgumentException("Object is not a SearchResultItem");
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
        return backingList.removeAll(c);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return backingList.retainAll(c);
    }

    @Override
    public void clear() {

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

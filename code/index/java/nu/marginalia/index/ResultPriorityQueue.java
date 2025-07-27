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
 * Since the expected use case is to add a large number of items
 * and then iterate over the items, the class is optimized for
 * this scenario, and does not implement other mutating methods
 * than addAll().
 */
public class ResultPriorityQueue implements Iterable<SearchResultItem> {
    private final LongOpenHashSet idsInSet = new LongOpenHashSet();
    private final MinMaxPriorityQueue<SearchResultItem> queue;

    private int itemsProcessed = 0;

    public ResultPriorityQueue(int limit) {
        this.queue = MinMaxPriorityQueue.<SearchResultItem>orderedBy(Comparator.naturalOrder()).maximumSize(limit).create();
    }

    public Iterator<SearchResultItem> iterator() {
        return queue.iterator();
    }

    /** Adds all items to the queue, and returns true if any items were added.
     * This is a thread-safe operation.
     */
    public synchronized boolean addAll(@NotNull Collection<? extends SearchResultItem> items) {
        itemsProcessed+=items.size();

        for (var item : items) {
            if (idsInSet.add(item.getDocumentId())) {
                queue.add(item);
            }
        }

        return true;
    }

    public int size() {
        return queue.size();
    }
    public int getItemsProcessed() {
        return itemsProcessed;
    }
    public boolean isEmpty() {
        return queue.isEmpty();
    }

}

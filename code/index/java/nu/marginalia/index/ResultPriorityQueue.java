package nu.marginalia.index;

import com.google.common.collect.MinMaxPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.index.model.RankableDocument;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** A priority queue for search results. This class is not thread-safe,
 * in general, except for concurrent use of the addAll method.
 * <p></p>
 * Since the expected use case is to add a large number of items
 * and then iterate over the items, the class is optimized for
 * this scenario, and does not implement other mutating methods
 * than addAll().
 */
public class ResultPriorityQueue implements Iterable<RankableDocument> {
    private final MinMaxPriorityQueue<RankableDocument> queue;
    private int itemsProcessed = 0;

    public ResultPriorityQueue(int limit) {
        this.queue = MinMaxPriorityQueue.<RankableDocument>orderedBy(Comparator.naturalOrder()).maximumSize(limit).create();
    }

    public @NotNull Iterator<RankableDocument> iterator() {
        return queue.iterator();
    }

    public boolean add(@NotNull RankableDocument document) {
        if (document.item == null)
            return false;


        synchronized (this) {
            itemsProcessed++;
            queue.add(document);
        }

        return true;
    }

    public synchronized List<RankableDocument> toList() {
        return new ArrayList<>(queue);
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

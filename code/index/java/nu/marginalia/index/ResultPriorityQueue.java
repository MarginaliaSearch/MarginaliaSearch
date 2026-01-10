package nu.marginalia.index;

import com.google.common.collect.MinMaxPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
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
    private final LongOpenHashSet idsInSet = new LongOpenHashSet();
    private final ArrayList<RankableDocument> queue;
    private final int limit;
    private int itemsProcessed = 0;

    private double worstScore = 0;

    public ResultPriorityQueue(int limit) {
        this.queue = new ArrayList<>(limit);
        this.limit = limit;
    }

    public @NotNull Iterator<RankableDocument> iterator() {
        return queue.iterator();
    }

    /** Adds all items to the queue, and returns true if any items were added.
     * This is a thread-safe operation.
     */
    public synchronized boolean add(@NotNull RankableDocument item) {
        itemsProcessed++;

        double newScore = item.item.getScore();

        if (queue.size() < limit) {
            if (idsInSet.add(item.combinedDocumentId)) {
                queue.add(item);
            }
            else {
                return false;
            }

            if (newScore < worstScore)
                queue.sort(Comparator.naturalOrder());
            else
                worstScore = newScore;
        }
        else if (newScore > worstScore) {
            return false;
        }

        if (idsInSet.add(item.combinedDocumentId)) {
            queue.add(item);
        }
        queue.sort(Comparator.naturalOrder());
        if (queue.size() > limit) {
            idsInSet.remove(queue.removeLast().combinedDocumentId);
            worstScore = queue.getLast().item.getScore();
        }

        return true;
    }

    public synchronized List<RankableDocument> toList() {
        return queue;
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

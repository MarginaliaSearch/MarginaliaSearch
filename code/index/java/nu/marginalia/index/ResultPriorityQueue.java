package nu.marginalia.index;

import com.google.common.collect.MinMaxPriorityQueue;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.index.model.RankableDocument;
import nu.marginalia.model.id.UrlIdCodec;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** A priority queue for search results. This class is not thread-safe.
 */
public class ResultPriorityQueue implements Iterable<RankableDocument> {
    private final MinMaxPriorityQueue<RankableDocument> queue;
    private final Int2IntOpenHashMap resultsPerDomain = new Int2IntOpenHashMap(10_000);

    private int itemsProcessed = 0;

    public ResultPriorityQueue(int limit) {
        this.queue = MinMaxPriorityQueue.<RankableDocument>orderedBy(Comparator.naturalOrder()).maximumSize(limit).create();
    }

    public @NotNull Iterator<RankableDocument> iterator() {
        return queue.iterator();
    }

    public void addAll(ResultPriorityQueue otherQueue) {
        queue.addAll(otherQueue.queue);
        otherQueue.resultsPerDomain.int2IntEntrySet().fastForEach(entry -> {
            resultsPerDomain.addTo(entry.getIntKey(), entry.getIntValue());
        });
        itemsProcessed += otherQueue.itemsProcessed;
    }

    public boolean add(@NotNull RankableDocument document) {
        if (document.item == null)
            return false;

        int domainId = UrlIdCodec.getDomainId(document.combinedDocumentId);

        itemsProcessed++;
        queue.add(document);
        resultsPerDomain.addTo(domainId, 1);

        return true;
    }

    public int numResultsFromDomain(int domainId) {
        return resultsPerDomain.get(domainId);
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

package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import nu.marginalia.index.model.RankableDocument;
import nu.marginalia.model.id.UrlIdCodec;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** A priority queue for search results. This class is not thread-safe.
 */
public class ResultPriorityQueue implements Iterable<RankableDocument> {
    private final TreeSet<RankableDocument> queue;

    /** The number of results seen from each domain (including rejects) */
    private final Int2IntOpenHashMap resultsPerDomainSeen;

    /** The number of results currently held from each domain */
    private final Int2IntOpenHashMap resultsPerDomainHeld;

    private int itemsProcessed = 0;
    private final int limit;
    private final int domainLimit;

    public ResultPriorityQueue(int limit, int domainLimit) {
        this.queue = new TreeSet<>(Comparator.naturalOrder());

        this.resultsPerDomainHeld = new Int2IntOpenHashMap(limit);
        this.resultsPerDomainHeld.defaultReturnValue(0);

        this.resultsPerDomainSeen = new Int2IntOpenHashMap(2_500);
        this.resultsPerDomainSeen.defaultReturnValue(0);

        this.limit = limit;
        this.domainLimit = domainLimit;
    }

    public @NotNull Iterator<RankableDocument> iterator() {
        return queue.iterator();
    }

    public void addAll(ResultPriorityQueue otherQueue) {
        for (var doc : otherQueue) {
            // Add with no statistics
            add(doc, false);
        }

        // Merge the statistics
        otherQueue.resultsPerDomainSeen.int2IntEntrySet().fastForEach(entry -> {
            resultsPerDomainSeen.addTo(entry.getIntKey(), entry.getIntValue());
        });
        itemsProcessed += otherQueue.itemsProcessed;
    }

    public boolean add(@NotNull RankableDocument document) {
        return add(document, true);
    }

    private boolean add(@NotNull RankableDocument document, boolean updateStats) {
        if (document.item == null)
            return false;

        int domainId = UrlIdCodec.getDomainId(document.combinedDocumentId);

        if (updateStats) {
            resultsPerDomainSeen.addTo(domainId, 1);
            itemsProcessed++;
        }

        // Short circuit if we're already at the limit and this item is worse than the last one
        if (queue.size() >= limit) {
            var last = queue.last();
            if (last.item.compareTo(document.item) <= 0) {
                return false;
            }
        }

        queue.add(document);

        resultsPerDomainHeld.addTo(domainId, 1);

        pruneDomain(domainId);
        removeExcessItems();

        return true;
    }


    private void removeExcessItems() {

        while (queue.size() > limit) {
            var item = queue.pollLast();

            if (1 == resultsPerDomainHeld.addTo(item.domainId(), -1)) {
                resultsPerDomainHeld.remove(item.domainId());
            }
        }

    }

    private void pruneDomain(int domainId) {
        int heldCount = resultsPerDomainHeld.get(domainId);

        if (heldCount < domainLimit) {
            return;
        }

        for (var iter = queue.reversed().iterator(); iter.hasNext() && heldCount > domainLimit; ) {
            var item = iter.next();

            if (item.domainId() != domainId)
                continue;

            iter.remove();
            resultsPerDomainHeld.addTo(domainId, -1);
            heldCount--;
        }
    }

    public int numResultsFromDomain(int domainId) {
        return resultsPerDomainSeen.get(domainId);
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

package nu.marginalia.wmsa.edge.dating;

import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;

import java.util.LinkedList;

public class DatingSessionObject {
    public final LinkedList<BrowseResult> queue = new LinkedList<>();
    public final LinkedList<BrowseResult> recentlyViewed = new LinkedList<>();
    private BrowseResult current;

    private static final int MAX_HISTORY_SIZE = 100;
    private static final int MAX_QUEUE_SIZE = 100;

    public BrowseResult setCurrent(BrowseResult result) {
        current = result;
        return current;
    }

    public BrowseResult next(EdgeDataStoreDao dao, EdgeDomainBlacklist blacklist) {
        if (queue.isEmpty()) {
            dao.getRandomDomains(25, blacklist, 0).forEach(queue::addLast);
        }
        return queue.pollFirst();
    }

    public BrowseResult nextSimilar(EdgeId<EdgeDomain> id, EdgeDataStoreDao dao, EdgeDomainBlacklist blacklist) {
        dao.getDomainNeighborsAdjacent(id, blacklist, 25).forEach(queue::addFirst);

        while (queue.size() > MAX_QUEUE_SIZE) {
            queue.removeLast();
        }

        return queue.pollFirst();
    }

    public void browseForward(BrowseResult res) {
        if (current != null) {
            addToHistory(current);
        }
        setCurrent(res);
    }

    public void browseBackward(BrowseResult res) {
        if (current != null) {
            addToQueue(current);
        }
        setCurrent(res);
    }

    public BrowseResult addToHistory(BrowseResult res) {
        recentlyViewed.addFirst(res);
        while (recentlyViewed.size() > MAX_HISTORY_SIZE) {
            recentlyViewed.removeLast();
        }
        return res;
    }

    public BrowseResult addToQueue(BrowseResult res) {
        queue.addFirst(res);
        while (queue.size() > MAX_QUEUE_SIZE) {
            queue.removeLast();
        }
        return res;
    }

    public BrowseResult takeFromHistory() {
        return recentlyViewed.pollFirst();
    }

    public boolean hasHistory() {
        return !recentlyViewed.isEmpty();
    }

    public boolean isRecent(BrowseResult res) {
        return recentlyViewed.contains(res) || res.equals(current);
    }
    public void resetQueue() {
        queue.clear();
    }

    public BrowseResult getCurrent() {
         return current;
    }
}

package nu.marginalia.index;

import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.index.model.RankableDocument;
import nu.marginalia.model.id.UrlIdCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResultPriorityQueueTest {

    @Test
    public void testAddWithinLimit() {
        var q = new ResultPriorityQueue(5, 5);

        assertTrue(q.add(doc(1, 1, 1.0)));
        assertTrue(q.add(doc(2, 1, 2.0)));
        assertTrue(q.add(doc(3, 1, 3.0)));

        assertEquals(3, q.size());
        assertEquals(List.of(1.0, 2.0, 3.0), scoresInOrder(q));
    }

    @Test
    public void testKeepBest() {
        var q = new ResultPriorityQueue(3, 5);

        q.add(doc(1, 1, 9.0));
        q.add(doc(2, 1, 8.0));
        q.add(doc(3, 1, 7.0));
        q.add(doc(4, 1, 2.0));
        q.add(doc(5, 1, 1.0));
        q.add(doc(6, 1, 5.0));

        assertEquals(3, q.size());
        assertEquals(List.of(1.0, 2.0, 5.0), scoresInOrder(q));
    }

    @Test
    public void testReplaceWhenFull() {
        var q = new ResultPriorityQueue(3, 5);
        q.add(doc(1, 1, 5.0));
        q.add(doc(2, 1, 6.0));
        q.add(doc(3, 1, 7.0));

        // Should be accepted
        assertTrue(q.add(doc(4, 1, 4.0)));
        assertEquals(3, q.size());
        assertEquals(List.of(4.0, 5.0, 6.0), scoresInOrder(q));
    }

    @Test
    public void testRejectWhenFull() {
        var q = new ResultPriorityQueue(3, 5);
        q.add(doc(1, 1, 1.0));
        q.add(doc(2, 1, 2.0));
        q.add(doc(3, 1, 3.0));

        // Should be rejected early
        assertFalse(q.add(doc(4, 1, 9.0)));
        assertEquals(3, q.size());
        assertEquals(List.of(1.0, 2.0, 3.0), scoresInOrder(q));
    }

    @Test
    public void testNumResultsFromDomain() {
        // Adding includes items that get pruned or rejected at the domain cap.
        var q = new ResultPriorityQueue(100, 2);

        for (int i = 1; i <= 5; i++) {
            q.add(doc(7, i, i));
        }

        assertEquals(2, q.size());
        assertEquals(5, q.numResultsFromDomain(7));
    }

    @Test
    public void testMergeStats() {
        var a = new ResultPriorityQueue(10, 5);
        a.add(doc(1, 1, 1.0));
        a.add(doc(1, 2, 3.0));

        var b = new ResultPriorityQueue(10, 5);
        b.add(doc(2, 1, 2.0));
        b.add(doc(2, 2, 4.0));

        a.addAll(b);

        assertEquals(4, a.size());
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0), scoresInOrder(a));
        assertEquals(2, a.numResultsFromDomain(1));
        assertEquals(2, a.numResultsFromDomain(2));
        assertEquals(4, a.getItemsProcessed());
    }

    @Test
    public void testItemsProcessed() {
        var a = new ResultPriorityQueue(2, 5);

        a.add(doc(1, 1, 1.0));
        a.add(doc(1, 2, 2.0));
        a.add(doc(1, 3, 3.0));
        a.add(doc(1, 4, 4.0));
        assertEquals(4, a.getItemsProcessed());

        var b = new ResultPriorityQueue(2, 5);
        b.add(doc(2, 1, 1.5));
        b.add(doc(2, 2, 2.5));
        b.add(doc(2, 3, 3.5));
        assertEquals(3, b.getItemsProcessed());

        a.addAll(b);

        assertEquals(7, a.getItemsProcessed());
    }

    public static RankableDocument doc(int domainId, int ordinal, double score) {
        long combinedId = UrlIdCodec.encodeId(domainId, ordinal);
        RankableDocument d = new RankableDocument(combinedId);
        d.item = new SearchResultItem(combinedId, 0L, 0, score, 0L);
        return d;
    }

    public static List<Double> scoresInOrder(ResultPriorityQueue q) {
        List<Double> out = new ArrayList<>();
        for (RankableDocument d : q) {
            out.add(d.item.getScore());
        }
        return out;
    }
}

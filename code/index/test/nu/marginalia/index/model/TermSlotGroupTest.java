package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.LongToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TermSlotGroupTest {

    // Term ids are the term index times ten, to keep them apart from the indices
    private static CompiledQueryLong query(int termCount, int[] variantClasses, IntList... paths) {
        String[] terms = new String[termCount];
        for (int i = 0; i < termCount; i++) {
            terms[i] = String.valueOf(10L * i);
        }
        return new CompiledQuery<>(List.of(paths), variantClasses, terms).mapToLong(Long::parseLong);
    }

    @Test
    void testSame() {
        // elden ( ring | rings )
        var query = query(3, new int[] { 0, 1, 1 }, IntList.of(0, 1), IntList.of(0, 2));

        var groups = TermSlotGroup.fromPaths(query, query.variantClasses);

        assertEquals(List.of(new TermSlotGroup(List.of(LongList.of(0), LongList.of(10, 20)))), groups);
    }

    @Test
    void testDifferent() {
        // elden ( ring | rings ) | elden_ring
        var query = query(4, new int[] { 0, 1, 1, 3 }, IntList.of(0, 1), IntList.of(0, 2), IntList.of(3));

        var groups = TermSlotGroup.fromPaths(query, query.variantClasses);

        assertEquals(List.of(
                new TermSlotGroup(List.of(LongList.of(0), LongList.of(10, 20))),
                new TermSlotGroup(List.of(LongList.of(30)))
        ), groups);
    }

    @Test
    void testProduct() {
        // ( 8 | viii ) ( 9 | ix ), all four paths
        var query = query(4, new int[] { 0, 1, 0, 1 },
                IntList.of(0, 1), IntList.of(0, 3), IntList.of(2, 1), IntList.of(2, 3));

        var groups = TermSlotGroup.fromPaths(query, query.variantClasses);

        assertEquals(List.of(new TermSlotGroup(List.of(LongList.of(0, 20), LongList.of(10, 30)))), groups);
    }

    @Test
    void testCheapest() {
        var group = new TermSlotGroup(List.of(LongList.of(0), LongList.of(10, 20), LongList.of(30)));

        var plans = group.plan(hits(0, 100, 10, 500, 20, 900, 30, 50), 10_000);

        assertEquals(List.of(
                new TermSlotGroup.Plan(30, List.of(LongList.of(0), LongList.of(10, 20)))
        ), plans);
    }

    @Test
    void testAlternatives() {
        var group = new TermSlotGroup(List.of(LongList.of(0, 10), LongList.of(20)));

        var plans = group.plan(hits(0, 50, 10, 60, 20, 1000), 10_000);

        assertEquals(List.of(
                new TermSlotGroup.Plan(0, List.of(LongList.of(20))),
                new TermSlotGroup.Plan(10, List.of(LongList.of(20)))
        ), plans);
    }

    @Test
    void testAlernatives2() {
        // henry ( viii | 8 ), viii is cheaper to retrieve on its own than to probe every henry against
        var group = new TermSlotGroup(List.of(LongList.of(0), LongList.of(10, 20)));

        var plans = group.plan(hits(0, 261_000, 10, 32_000, 20, 5_000_000), 7_000_000);

        assertEquals(List.of(
                new TermSlotGroup.Plan(10, List.of(LongList.of(0))),
                new TermSlotGroup.Plan(0, List.of(LongList.of(20)))
        ), plans);
    }

    @Test
    void testPlan2() {
        var group = new TermSlotGroup(List.of(LongList.of(0), LongList.of(10, 20), LongList.of(30)));

        var plans = group.plan(hits(0, 1000, 10, 10, 20, 20, 30, 5000), 10_000);

        assertEquals(List.of(
                new TermSlotGroup.Plan(10, List.of(LongList.of(0), LongList.of(30))),
                new TermSlotGroup.Plan(20, List.of(LongList.of(0), LongList.of(30)))
        ), plans);
    }

    private static LongToIntFunction hits(long... termsAndHits) {
        return termId -> {
            for (int i = 0; i < termsAndHits.length; i += 2) {
                if (termsAndHits[i] == termId) return (int) termsAndHits[i + 1];
            }
            throw new IllegalArgumentException("No hits for " + termId);
        };
    }
}

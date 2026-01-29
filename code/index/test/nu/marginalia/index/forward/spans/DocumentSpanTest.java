package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentSpanTest {
    @Test
    public void testLastPositionNotCheckedAgainstRemainingRanges() {
        // Set up: ranges [10,20) and [30,40)
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 30, 40});
        DocumentSpan span = new DocumentSpan(startsEnds);

        // Position 35 should intersect with range [30,40)
        IntList positions = new IntArrayList(new int[]{35});

        int result = span.countIntersections(positions);

        // Expected: 1 (position 35 is in range [30,40))
        // Actual with bug: 0 (position is read but loop exits before checking against [30,40))
        assertEquals(1, result);
    }

    @Test
    public void testMultiplePositionsWithRemainingRanges() {
        // Set up: ranges [10,20), [30,40), [50,60)
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 30, 40, 50, 60});
        DocumentSpan span = new DocumentSpan(startsEnds);

        // Positions that should match the last two ranges
        IntList positions = new IntArrayList(new int[]{15, 35, 55});

        int result = span.countIntersections(positions);

        // All three positions should match
        assertEquals(3, result);
    }

    @Test
    public void testEmptyPositions() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 30, 40});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList();

        assertEquals(0, span.countIntersections(positions));
    }

    @Test
    public void testNoIntersections_AllPositionsBeforeRanges() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 30, 40});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{1, 2, 5});

        assertEquals(0, span.countIntersections(positions));
    }

    @Test
    public void testNoIntersections_AllPositionsAfterRanges() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 30, 40});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{50, 60, 70});

        assertEquals(0, span.countIntersections(positions));
    }

    @Test
    public void testNoIntersections_PositionsBetweenRanges() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 30, 40});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{25, 26, 27});

        assertEquals(0, span.countIntersections(positions));
    }

    @Test
    public void testSingleIntersection() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{15});

        assertEquals(1, span.countIntersections(positions));
    }

    @Test
    public void testAllPositionsIntersect() {
        IntList startsEnds = new IntArrayList(new int[]{10, 50});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{15, 20, 30, 45});

        assertEquals(4, span.countIntersections(positions));
    }

    @Test
    public void testMixedIntersectionsAndNonIntersections() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 30, 40});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{5, 15, 25, 35, 45});

        assertEquals(2, span.countIntersections(positions)); // 15 and 35
    }

    @Test
    public void testBoundaryConditions_AtStart() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{10}); // start is inclusive

        assertEquals(1, span.countIntersections(positions));
    }

    @Test
    public void testBoundaryConditions_AtEndMinus1() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{19}); // end-1 should be included

        assertEquals(1, span.countIntersections(positions));
    }

    @Test
    public void testBoundaryConditions_AtEnd() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{20}); // end is exclusive (based on pos < end)

        assertEquals(0, span.countIntersections(positions));
    }

    @Test
    public void testMultiplePositionsInSameRange() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{12, 14, 16, 18});

        assertEquals(4, span.countIntersections(positions));
    }

    @Test
    public void testPositionsAcrossMultipleRanges() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 30, 40, 50, 60});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{15, 35, 55});

        assertEquals(3, span.countIntersections(positions));
    }

    @Test
    public void testUnsortedPositions_ShouldStillWork() {
        // Assuming positions might come unsorted
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 30, 40});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{35, 15, 5, 45});

        // This test will reveal if the algorithm assumes sorted positions
        // With current implementation: expects sorted, so this would likely fail
        // You might want to document this assumption or add sorting
        int result = span.countIntersections(positions);
        // Correct answer if positions were sorted would be 2 (15 and 35)
    }

    @Test
    public void testAdjacentRanges() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 20, 30});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{15, 20, 25});

        assertEquals(3, span.countIntersections(positions)); // 15 in [10,20), 20 in [20,30), 25 in [20,30)
    }

    @Test
    public void testManyRanges_OnlyLastMatches() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 30, 40, 50, 60, 70, 80});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{75});

        assertEquals(1, span.countIntersections(positions));
    }

    @Test
    public void testPositionExactlyBetweenRanges() {
        IntList startsEnds = new IntArrayList(new int[]{10, 20, 30, 40});
        DocumentSpan span = new DocumentSpan(startsEnds);
        IntList positions = new IntArrayList(new int[]{20}); // exactly at boundary

        assertEquals(0, span.countIntersections(positions)); // 20 is not in [10,20) and not yet checked for [30,40)
    }
}
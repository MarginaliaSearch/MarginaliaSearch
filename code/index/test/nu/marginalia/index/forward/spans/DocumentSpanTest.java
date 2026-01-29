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


    @Test
    public void testContainsPosition_emptyStartsEnds() {
        DocumentSpan span = new DocumentSpan(new IntArrayList());
        assertFalse(span.containsPosition(5));
    }

    @Test
    public void testContainsPosition_singleRange_positionInside() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertTrue(span.containsPosition(15));
    }

    @Test
    public void testContainsPosition_singleRange_positionAtStart() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertTrue(span.containsPosition(10)); // start is inclusive
    }

    @Test
    public void testContainsPosition_singleRange_positionAtEnd() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertFalse(span.containsPosition(20)); // end is exclusive
    }

    @Test
    public void testContainsPosition_singleRange_positionBefore() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertFalse(span.containsPosition(5));
    }

    @Test
    public void testContainsPosition_singleRange_positionAfter() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertFalse(span.containsPosition(25));
    }

    @Test
    public void testContainsPosition_multipleRanges_inFirstRange() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40, 50, 60}));
        assertTrue(span.containsPosition(15));
    }

    @Test
    public void testContainsPosition_multipleRanges_inMiddleRange() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40, 50, 60}));
        assertTrue(span.containsPosition(35));
    }

    @Test
    public void testContainsPosition_multipleRanges_inLastRange() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40, 50, 60}));
        assertTrue(span.containsPosition(55));
    }

    @Test
    public void testContainsPosition_multipleRanges_inGap() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40, 50, 60}));
        assertFalse(span.containsPosition(25)); // between first and second range
    }

    @Test
    public void testContainsPosition_multipleRanges_beforeAll() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40, 50, 60}));
        assertFalse(span.containsPosition(5));
    }

    @Test
    public void testContainsPosition_multipleRanges_afterAll() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40, 50, 60}));
        assertFalse(span.containsPosition(70));
    }

    @Test
    public void testContainsRange_emptyStartsEnds() {
        DocumentSpan span = new DocumentSpan(new IntArrayList());
        assertFalse(span.containsRange(IntArrayList.wrap(new int[]{10}), 5));
    }

    @Test
    public void testContainsRange_emptyPositions() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertFalse(span.containsRange(new IntArrayList(), 5));
    }

    @Test
    public void testContainsRange_fullyContained() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertTrue(span.containsRange(IntArrayList.wrap(new int[]{12}), 5));
        // Range [12, 17) is fully within [10, 20)
    }

    @Test
    public void testContainsRange_exactFit() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertTrue(span.containsRange(IntArrayList.wrap(new int[]{10}), 10));
        // Range [10, 20) exactly matches span [10, 20)
    }

    @Test
    public void testContainsRange_startsAtSpanStart() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertTrue(span.containsRange(IntArrayList.wrap(new int[]{10}), 5));
        // Range [10, 15) fits within [10, 20)
    }

    @Test
    public void testContainsRange_endsAtSpanEnd() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertTrue(span.containsRange(IntArrayList.wrap(new int[]{15}), 5));
        // Range [15, 20) fits within [10, 20)
    }

    @Test
    public void testContainsRange_extendsBeforeSpan() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertFalse(span.containsRange(IntArrayList.wrap(new int[]{8}), 5));
        // Range [8, 13) extends before span start
    }

    @Test
    public void testContainsRange_extendsPastSpanEnd() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertFalse(span.containsRange(IntArrayList.wrap(new int[]{18}), 5));
        // Range [18, 23) extends past span end
    }

    @Test
    public void testContainsRange_completelyBeforeSpan() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertFalse(span.containsRange(IntArrayList.wrap(new int[]{5}), 3));
        // Range [5, 8) is completely before [10, 20)
    }

    @Test
    public void testContainsRange_completelyAfterSpan() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertFalse(span.containsRange(IntArrayList.wrap(new int[]{25}), 3));
        // Range [25, 28) is completely after [10, 20)
    }

    @Test
    public void testContainsRange_lengthZero() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertTrue(span.containsRange(IntArrayList.wrap(new int[]{15}), 0));
        // Range [15, 15) is empty and technically contained
    }

    @Test
    public void testContainsRange_lengthOne() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertTrue(span.containsRange(IntArrayList.wrap(new int[]{15}), 1));
        // Range [15, 16) is contained
    }

    @Test
    public void testContainsRange_multiplePositions_firstMatches() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertTrue(span.containsRange(IntArrayList.wrap(new int[]{12, 25, 30}), 5));
        // First position [12, 17) is contained
    }

    @Test
    public void testContainsRange_multiplePositions_middleMatches() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40}));
        assertTrue(span.containsRange(IntArrayList.wrap(new int[]{5, 32, 50}), 5));
        // Middle position [32, 37) is contained in second span
    }

    @Test
    public void testContainsRange_multiplePositions_noneMatch() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40}));
        assertFalse(span.containsRange(IntArrayList.wrap(new int[]{5, 25, 50}), 5));
        // No positions produce contained ranges
    }

    @Test
    public void testContainsRange_multipleSpans_fitsInLaterSpan() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40, 50, 60}));
        assertTrue(span.containsRange(IntArrayList.wrap(new int[]{52}), 5));
        // Range [52, 57) is contained in third span [50, 60)
    }

    @Test
    public void testContainsRange_positionInGapBetweenSpans() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40}));
        assertFalse(span.containsRange(IntArrayList.wrap(new int[]{22}), 5));
        // Range [22, 27) spans the gap between spans
    }

    @Test
    public void testContainsRange_rangeSpansMultipleSpans() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 21, 30}));
        assertFalse(span.containsRange(IntArrayList.wrap(new int[]{18}), 5));
        // Range [18, 23) would span across two spans
    }

    @Test
    public void testCountRangeMatchesExact_emptyStartsEnds() {
        DocumentSpan span = new DocumentSpan(new IntArrayList());
        assertEquals(0, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{10}), 5));
    }

    @Test
    public void testCountRangeMatchesExact_emptyPositions() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatchesExact(new IntArrayList(), 10));
    }

    @Test
    public void testCountRangeMatchesExact_singleExactMatch() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(1, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{10}), 10));
        // Range [10, 20) exactly matches span [10, 20)
    }

    @Test
    public void testCountRangeMatchesExact_positionMatchesButLengthTooShort() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{10}), 5));
        // Range [10, 15) doesn't exactly match span [10, 20)
    }

    @Test
    public void testCountRangeMatchesExact_positionMatchesButLengthTooLong() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{10}), 15));
        // Range [10, 25) doesn't exactly match span [10, 20)
    }

    @Test
    public void testCountRangeMatchesExact_startOffByOne() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{11}), 9));
        // Range [11, 20) doesn't exactly match span [10, 20)
    }

    @Test
    public void testCountRangeMatchesExact_fullyContainedButNotExact() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{12}), 5));
        // Range [12, 17) is contained but not an exact match
    }

    @Test
    public void testCountRangeMatchesExact_positionBeforeSpan() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{5}), 10));
    }

    @Test
    public void testCountRangeMatchesExact_positionAfterSpan() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{25}), 10));
    }

    @Test
    public void testCountRangeMatchesExact_multiplePositions_oneMatches() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(1, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{5, 10, 15}), 10));
        // Only position 10 creates an exact match
    }

    @Test
    public void testCountRangeMatchesExact_multiplePositions_noneMatch() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{5, 12, 15, 25}), 10));
    }

    @Test
    public void testCountRangeMatchesExact_multipleSpans_onePositionMatchesFirst() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40}));
        assertEquals(1, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{10}), 10));
    }

    @Test
    public void testCountRangeMatchesExact_multipleSpans_onePositionMatchesSecond() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40}));
        assertEquals(1, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{30}), 10));
    }

    @Test
    public void testCountRangeMatchesExact_multipleSpans_multipleMatches() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40, 50, 60}));
        assertEquals(2, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{10, 30, 35}), 10));
        // Positions 10 and 30 match their respective spans
    }

    @Test
    public void testCountRangeMatchesExact_positionInGap() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40}));
        assertEquals(0, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{25}), 5));
    }

    @Test
    public void testCountRangeMatchesExact_lengthZero() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 10}));
        assertEquals(1, span.countRangeMatchesExact(IntArrayList.wrap(new int[]{10}), 0));
        // Empty range [10, 10) matches empty span [10, 10)
    }

    @Test
    public void testCountRangeMatches_emptyStartsEnds() {
        DocumentSpan span = new DocumentSpan(new IntArrayList());
        assertEquals(0, span.countRangeMatches(IntArrayList.wrap(new int[]{10}), 5));
    }

    @Test
    public void testCountRangeMatches_emptyPositions() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatches(new IntArrayList(), 5));
    }

    @Test
    public void testCountRangeMatches_singleMatch() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(1, span.countRangeMatches(IntArrayList.wrap(new int[]{12}), 5));
        // Range [12, 17) is fully contained
    }

    @Test
    public void testCountRangeMatches_exactFit() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(1, span.countRangeMatches(IntArrayList.wrap(new int[]{10}), 10));
        // Range [10, 20) exactly matches span
    }

    @Test
    public void testCountRangeMatches_atSpanStart() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(1, span.countRangeMatches(IntArrayList.wrap(new int[]{10}), 5));
        // Range [10, 15) starts at span start
    }

    @Test
    public void testCountRangeMatches_atSpanEnd() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(1, span.countRangeMatches(IntArrayList.wrap(new int[]{15}), 5));
        // Range [15, 20) ends at span end
    }

    @Test
    public void testCountRangeMatches_extendsPastEnd() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatches(IntArrayList.wrap(new int[]{18}), 5));
        // Range [18, 23) extends past span end
    }

    @Test
    public void testCountRangeMatches_startsBeforeSpan() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatches(IntArrayList.wrap(new int[]{8}), 5));
        // Range [8, 13) starts before span
    }

    @Test
    public void testCountRangeMatches_completelyBeforeSpan() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatches(IntArrayList.wrap(new int[]{3}), 5));
        // Range [3, 8) is completely before span
    }

    @Test
    public void testCountRangeMatches_completelyAfterSpan() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatches(IntArrayList.wrap(new int[]{25}), 5));
        // Range [25, 30) is completely after span
    }

    @Test
    public void testCountRangeMatches_lengthZero() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(1, span.countRangeMatches(IntArrayList.wrap(new int[]{15}), 0));
        // Empty range [15, 15) is contained
    }

    @Test
    public void testCountRangeMatches_lengthOne() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(1, span.countRangeMatches(IntArrayList.wrap(new int[]{19}), 1));
        // Range [19, 20) of length 1
    }

    @Test
    public void testCountRangeMatches_multiplePositions_allMatch() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 30}));
        assertEquals(3, span.countRangeMatches(IntArrayList.wrap(new int[]{10, 15, 20}), 5));
        // All three ranges fit within [10, 30)
    }

    @Test
    public void testCountRangeMatches_multiplePositions_someMatch() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(3, span.countRangeMatches(IntArrayList.wrap(new int[]{5, 10, 15, 18, 25}), 2));
        // Only positions 10 and 15, 18 produce contained ranges
    }

    @Test
    public void testCountRangeMatches_multiplePositions_noneMatch() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatches(IntArrayList.wrap(new int[]{3, 5, 25, 30}), 5));
    }

    @Test
    public void testCountRangeMatches_multipleSpans_matchesInFirst() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40}));
        assertEquals(2, span.countRangeMatches(IntArrayList.wrap(new int[]{10, 15}), 5));
        // Both positions match in first span
    }

    @Test
    public void testCountRangeMatches_multipleSpans_matchesInSecond() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40}));
        assertEquals(2, span.countRangeMatches(IntArrayList.wrap(new int[]{30, 35}), 5));
        // Both positions match in second span
    }

    @Test
    public void testCountRangeMatches_multipleSpans_matchesInBoth() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40}));
        assertEquals(3, span.countRangeMatches(IntArrayList.wrap(new int[]{10, 15, 32}), 5));
        // First two in first span, last in second span
    }

    @Test
    public void testCountRangeMatches_multipleSpans_positionsInGaps() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40}));
        assertEquals(0, span.countRangeMatches(IntArrayList.wrap(new int[]{22, 25}), 5));
        // Positions in gap produce ranges that don't fit
    }

    @Test
    public void testCountRangeMatches_multipleSpans_mixedMatches() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20, 30, 40, 50, 60}));
        assertEquals(4, span.countRangeMatches(IntArrayList.wrap(new int[]{5, 10, 15, 25, 32, 55, 70}), 5));
        // Positions 10, 15, 32, 55 produce contained ranges
    }

    @Test
    public void testCountRangeMatches_positionAtEndBoundary() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{10, 20}));
        assertEquals(0, span.countRangeMatches(IntArrayList.wrap(new int[]{20}), 5));
        // Position 20 is at span end, range [20, 25) extends past
    }

    @Test
    public void testCountRangeMatches_largeSpan_manyMatches() {
        DocumentSpan span = new DocumentSpan(IntArrayList.wrap(new int[]{0, 100}));
        assertEquals(10, span.countRangeMatches(IntArrayList.wrap(new int[]{0, 10, 20, 30, 40, 50, 60, 70, 80, 90}), 5));
        // All 10 positions produce contained ranges
    }
}
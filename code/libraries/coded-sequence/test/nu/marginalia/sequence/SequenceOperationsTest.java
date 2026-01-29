package nu.marginalia.sequence;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class SequenceOperationsTest {

    private IntArrayList resultList;

    @BeforeEach
    public void setUp() {
        resultList = new IntArrayList();
    }

    @Test
    void intersectSequencesSingle() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1);

        assertTrue(SequenceOperations.intersectSequences(seq1.iterator()));
    }

    @Test
    void intersectSequencesTrivialMatch() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 1);

        assertTrue(SequenceOperations.intersectSequences(seq1.iterator(), seq2.iterator()));
    }

    @Test
    void intersectSequencesTrivialMismatch() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 2);

        assertFalse(SequenceOperations.intersectSequences(seq1.iterator(), seq2.iterator()));
    }

    @Test
    void intersectSequencesOffsetMatch() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 3);

        assertTrue(SequenceOperations.intersectSequences(seq1.offsetIterator(0), seq2.offsetIterator(-2)));
    }

    @Test
    void intersectSequencesDeepMatch() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1, 3, 4, 7, 8, 9, 11);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 2, 5, 8, 14);

        assertTrue(SequenceOperations.intersectSequences(seq1.iterator(), seq2.iterator()));
    }

    @Test
    void intersectSequencesDeepMatch3() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1, 3, 4, 7, 8, 9, 11);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 2, 5, 8, 14);
        GammaCodedSequence seq3 = GammaCodedSequence.generate(wa, 1, 5, 8, 9);

        assertTrue(SequenceOperations.intersectSequences(seq1.iterator(), seq2.iterator(), seq3.iterator()));
    }



    @Test
    void intersectSequencesDeepMatch3findIntersections() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1, 3, 4, 7, 8, 9, 10, 11);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 2, 5, 8, 10, 14);
        GammaCodedSequence seq3 = GammaCodedSequence.generate(wa, 1, 5, 8, 9, 10);

        assertEquals(IntList.of(8, 10), SequenceOperations.findIntersections(new IntArrayList(), seq1.values(), seq2.values(), seq3.values()));
    }


    @Test
    void intersectSequencesDeepMismatch() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1, 3, 4, 7, 8, 9, 11);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 2, 5, 14);

        assertFalse(SequenceOperations.intersectSequences(seq1.iterator(), seq2.iterator()));
    }

    @Test
    void testMinDistance() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 11, 80, 160);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 20, 50, 100);
        GammaCodedSequence seq3 = GammaCodedSequence.generate(wa, 30, 60, 90);

        assertEquals(19, SequenceOperations.minDistance(new IntList[]{seq1.values(), seq2.values(), seq3.values()}));
    }


    // ========== intersectSequences Tests ==========

    @Test
    public void testIntersectSequences_EmptyArray() {
        boolean result = SequenceOperations.intersectSequences();
        assertTrue(result, "Empty array should return true");
    }

    @Test
    public void testIntersectSequences_SingleSequence() {
        IntIterator iter = IntIterators.wrap(new int[]{1, 2, 3});
        boolean result = SequenceOperations.intersectSequences(iter);
        assertTrue(result, "Single sequence should return true");
    }

    @Test
    public void testIntersectSequences_SingleEmptySequence() {
        IntIterator iter = IntIterators.EMPTY_ITERATOR;
        boolean result = SequenceOperations.intersectSequences(iter);
        assertTrue(result, "Single empty sequence should return true");
    }

    @Test
    public void testIntersectSequences_TwoSequencesWithIntersection() {
        IntIterator iter1 = IntIterators.wrap(new int[]{1, 3, 5, 7, 9});
        IntIterator iter2 = IntIterators.wrap(new int[]{2, 3, 6, 9, 12});
        boolean result = SequenceOperations.intersectSequences(iter1, iter2);
        assertTrue(result, "Sequences should intersect at 3");
    }

    @Test
    public void testIntersectSequences_TwoSequencesNoIntersection() {
        IntIterator iter1 = IntIterators.wrap(new int[]{1, 2, 3});
        IntIterator iter2 = IntIterators.wrap(new int[]{4, 5, 6});
        boolean result = SequenceOperations.intersectSequences(iter1, iter2);
        assertFalse(result, "Sequences should not intersect");
    }

    @Test
    public void testIntersectSequences_TwoSequencesFirstEmpty() {
        IntIterator iter1 = IntIterators.EMPTY_ITERATOR;
        IntIterator iter2 = IntIterators.wrap(new int[]{1, 2, 3});
        boolean result = SequenceOperations.intersectSequences(iter1, iter2);
        assertFalse(result, "Empty first sequence should return false");
    }

    @Test
    public void testIntersectSequences_TwoSequencesSecondEmpty() {
        IntIterator iter1 = IntIterators.wrap(new int[]{1, 2, 3});
        IntIterator iter2 = IntIterators.EMPTY_ITERATOR;
        boolean result = SequenceOperations.intersectSequences(iter1, iter2);
        assertFalse(result, "Empty second sequence should return false");
    }

    @Test
    public void testIntersectSequences_MultipleSequencesWithIntersection() {
        IntIterator iter1 = IntIterators.wrap(new int[]{1, 5, 10, 15, 20});
        IntIterator iter2 = IntIterators.wrap(new int[]{3, 5, 12, 15, 18});
        IntIterator iter3 = IntIterators.wrap(new int[]{2, 5, 7, 15, 22});
        boolean result = SequenceOperations.intersectSequences(iter1, iter2, iter3);
        assertTrue(result, "All three sequences should intersect at 5");
    }

    @Test
    public void testIntersectSequences_MultipleSequencesNoIntersection() {
        IntIterator iter1 = IntIterators.wrap(new int[]{1, 2, 3});
        IntIterator iter2 = IntIterators.wrap(new int[]{4, 5, 6});
        IntIterator iter3 = IntIterators.wrap(new int[]{7, 8, 9});
        boolean result = SequenceOperations.intersectSequences(iter1, iter2, iter3);
        assertFalse(result, "No common elements across all sequences");
    }

    @Test
    public void testIntersectSequences_AllSameValue() {
        IntIterator iter1 = IntIterators.wrap(new int[]{5, 5, 5});
        IntIterator iter2 = IntIterators.wrap(new int[]{5, 5});
        IntIterator iter3 = IntIterators.wrap(new int[]{5});
        boolean result = SequenceOperations.intersectSequences(iter1, iter2, iter3);
        assertTrue(result, "All sequences contain 5");
    }

    @Test
    public void testIntersectSequences_IntersectionAtEnd() {
        IntIterator iter1 = IntIterators.wrap(new int[]{1, 2, 3, 100});
        IntIterator iter2 = IntIterators.wrap(new int[]{50, 75, 100});
        boolean result = SequenceOperations.intersectSequences(iter1, iter2);
        assertTrue(result, "Should intersect at 100");
    }

    @Test
    public void testIntersectSequences_IntersectionAtBeginning() {
        IntIterator iter1 = IntIterators.wrap(new int[]{1, 50, 100});
        IntIterator iter2 = IntIterators.wrap(new int[]{1, 2, 3});
        boolean result = SequenceOperations.intersectSequences(iter1, iter2);
        assertTrue(result, "Should intersect at 1");
    }

    @Test
    public void testIntersectSequences_NegativeNumbers() {
        IntIterator iter1 = IntIterators.wrap(new int[]{-10, -5, 0, 5});
        IntIterator iter2 = IntIterators.wrap(new int[]{-8, -5, 3, 10});
        boolean result = SequenceOperations.intersectSequences(iter1, iter2);
        assertTrue(result, "Should intersect at -5");
    }

    // ========== intersectOffsetSequences Tests ==========

    @Test
    public void testIntersectOffsetSequences_EmptyArray() {
        boolean result = SequenceOperations.intersectOffsetSequences(new IntIterator[0], new int[0]);
        assertTrue(result, "Empty array should return true");
    }

    @Test
    public void testIntersectOffsetSequences_SingleSequence() {
        IntIterator[] iters = {IntIterators.wrap(new int[]{1, 2, 3})};
        int[] offsets = {0};
        boolean result = SequenceOperations.intersectOffsetSequences(iters, offsets);
        assertTrue(result, "Single sequence should return true");
    }

    @Test
    public void testIntersectOffsetSequences_NoOffsets() {
        IntIterator[] iters = {
                IntIterators.wrap(new int[]{1, 5, 10}),
                IntIterators.wrap(new int[]{3, 5, 12})
        };
        int[] offsets = {0, 0};
        boolean result = SequenceOperations.intersectOffsetSequences(iters, offsets);
        assertTrue(result, "Should intersect at 5 with no offsets");
    }

    @Test
    public void testIntersectOffsetSequences_WithPositiveOffsets() {
        IntIterator[] iters = {
                IntIterators.wrap(new int[]{1, 2, 3}),  // becomes 1, 2, 3
                IntIterators.wrap(new int[]{0, 1, 2})   // becomes 1, 2, 3 with offset +1
        };
        int[] offsets = {0, 1};
        boolean result = SequenceOperations.intersectOffsetSequences(iters, offsets);
        assertTrue(result, "Should intersect with offset");
    }

    @Test
    public void testIntersectOffsetSequences_WithNegativeOffsets() {
        IntIterator[] iters = {
                IntIterators.wrap(new int[]{10, 20, 30}),  // becomes 5, 15, 25
                IntIterators.wrap(new int[]{15, 25, 35})   // becomes 15, 25, 35
        };
        int[] offsets = {-5, 0};
        boolean result = SequenceOperations.intersectOffsetSequences(iters, offsets);
        assertTrue(result, "Should intersect at 15 with negative offset");
    }

    @Test
    public void testIntersectOffsetSequences_OffsetsCreateIntersection() {
        IntIterator[] iters = {
                IntIterators.wrap(new int[]{1, 2, 3}),
                IntIterators.wrap(new int[]{8, 9, 10})
        };
        int[] offsets = {0, -5};  // Second becomes 3, 4, 5
        boolean result = SequenceOperations.intersectOffsetSequences(iters, offsets);
        assertTrue(result, "Offsets should create intersection at 3");
    }

    @Test
    public void testIntersectOffsetSequences_OffsetsPreventIntersection() {
        IntIterator[] iters = {
                IntIterators.wrap(new int[]{1, 2, 3}),
                IntIterators.wrap(new int[]{1, 2, 3})
        };
        int[] offsets = {0, 10};
        boolean result = SequenceOperations.intersectOffsetSequences(iters, offsets);
        assertFalse(result, "Offset should prevent intersection");
    }

    @Test
    public void testIntersectOffsetSequences_EmptySequence() {
        IntIterator[] iters = {
                IntIterators.EMPTY_ITERATOR,
                IntIterators.wrap(new int[]{1, 2, 3})
        };
        int[] offsets = {0, 0};
        boolean result = SequenceOperations.intersectOffsetSequences(iters, offsets);
        assertFalse(result, "Empty sequence should return false");
    }

    // ========== findIntersections Tests ==========

    @Test
    public void testFindIntersections_EmptyArray() {
        IntList result = SequenceOperations.findIntersections(resultList);
        assertEquals(0, result.size(), "Empty array should return empty list");
    }

    @Test
    public void testFindIntersections_SingleList() {
        IntList list1 = IntArrayList.wrap(new int[]{1, 2, 3, 4, 5});
        IntList result = SequenceOperations.findIntersections(resultList, list1);
        assertEquals(5, result.size(), "Single list should return all elements");
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, result.toIntArray());
    }

    @Test
    public void testFindIntersections_EmptyList() {
        IntList list1 = IntArrayList.wrap(new int[]{1, 2, 3});
        IntList list2 = new IntArrayList();
        IntList result = SequenceOperations.findIntersections(resultList, list1, list2);
        assertEquals(0, result.size(), "Empty list should return empty result");
    }

    @Test
    public void testFindIntersections_TwoListsWithIntersections() {
        IntList list1 = IntArrayList.wrap(new int[]{1, 3, 5, 7, 9});
        IntList list2 = IntArrayList.wrap(new int[]{2, 3, 5, 8, 9});
        IntList result = SequenceOperations.findIntersections(resultList, list1, list2);
        assertArrayEquals(new int[]{3, 5, 9}, result.toIntArray(), "Should find intersections at 3, 5, 9");
    }

    @Test
    public void testFindIntersections_TwoListsNoIntersections() {
        IntList list1 = IntArrayList.wrap(new int[]{1, 2, 3});
        IntList list2 = IntArrayList.wrap(new int[]{4, 5, 6});
        IntList result = SequenceOperations.findIntersections(resultList, list1, list2);
        assertEquals(0, result.size(), "Should find no intersections");
    }

    @Test
    public void testFindIntersections_ThreeListsWithIntersections() {
        IntList list1 = IntArrayList.wrap(new int[]{1, 5, 10, 15, 20});
        IntList list2 = IntArrayList.wrap(new int[]{3, 5, 10, 15, 18});
        IntList list3 = IntArrayList.wrap(new int[]{2, 5, 10, 15, 22});
        IntList result = SequenceOperations.findIntersections(resultList, list1, list2, list3);
        assertArrayEquals(new int[]{5, 10, 15}, result.toIntArray(), "Should find intersections at 5, 10, 15");
    }

    @Test
    public void testFindIntersections_AllSameElements() {
        IntList list1 = IntArrayList.wrap(new int[]{1, 2, 3});
        IntList list2 = IntArrayList.wrap(new int[]{1, 2, 3});
        IntList list3 = IntArrayList.wrap(new int[]{1, 2, 3});
        IntList result = SequenceOperations.findIntersections(resultList, list1, list2, list3);
        assertArrayEquals(new int[]{1, 2, 3}, result.toIntArray(), "All elements should intersect");
    }

    @Test
    public void testFindIntersections_SingleIntersection() {
        IntList list1 = IntArrayList.wrap(new int[]{1, 2, 100});
        IntList list2 = IntArrayList.wrap(new int[]{50, 100, 200});
        IntList result = SequenceOperations.findIntersections(resultList, list1, list2);
        assertArrayEquals(new int[]{100}, result.toIntArray(), "Should find single intersection");
    }

    @Test
    public void testFindIntersections_WithOffsets() {
        IntList list1 = IntArrayList.wrap(new int[]{1, 2, 3});
        IntList list2 = IntArrayList.wrap(new int[]{0, 1, 2});
        int[] offsets = {0, 1};  // Second list becomes 1, 2, 3
        IntList result = SequenceOperations.findIntersections(
                resultList,
                new IntList[]{list1, list2},
                offsets,
                Integer.MAX_VALUE
        );
        assertArrayEquals(new int[]{1, 2, 3}, result.toIntArray(), "Should find all intersections with offset");
    }

    @Test
    public void testFindIntersections_WithNegativeOffsets() {
        IntList list1 = IntArrayList.wrap(new int[]{10, 20, 30});
        IntList list2 = IntArrayList.wrap(new int[]{15, 25, 35});
        int[] offsets = {-5, 0};  // First list becomes 5, 15, 25
        IntList result = SequenceOperations.findIntersections(
                resultList,
                new IntList[]{list1, list2},
                offsets,
                Integer.MAX_VALUE
        );
        assertArrayEquals(new int[]{15, 25}, result.toIntArray(), "Should find intersections with negative offset");
    }

    @Test
    public void testFindIntersections_WithLimitN() {
        IntList list1 = IntArrayList.wrap(new int[]{1, 2, 3, 4, 5});
        IntList list2 = IntArrayList.wrap(new int[]{1, 2, 3, 4, 5});
        int[] offsets = {0, 0};
        IntList result = SequenceOperations.findIntersections(
                resultList,
                new IntList[]{list1, list2},
                offsets,
                2
        );
        assertTrue(result.size() <= 3, "Should respect limit n (may return n+1)");
    }

    @Test
    public void testFindIntersections_DifferentLengthLists() {
        IntList list1 = IntArrayList.wrap(new int[]{1, 5, 10});
        IntList list2 = IntArrayList.wrap(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        IntList result = SequenceOperations.findIntersections(resultList, list1, list2);
        assertArrayEquals(new int[]{1, 5, 10}, result.toIntArray(), "Should handle different length lists");
    }

    @Test
    public void testFindIntersections_NegativeNumbers() {
        IntList list1 = IntArrayList.wrap(new int[]{-10, -5, 0, 5, 10});
        IntList list2 = IntArrayList.wrap(new int[]{-8, -5, 0, 3, 10});
        IntList result = SequenceOperations.findIntersections(resultList, list1, list2);
        assertArrayEquals(new int[]{-5, 0, 10}, result.toIntArray(), "Should handle negative numbers");
    }

    @Test
    public void testFindIntersections_ReuseResultList() {
        IntList list1 = IntArrayList.wrap(new int[]{1, 2, 3});
        IntList list2 = IntArrayList.wrap(new int[]{2, 3, 4});

        IntList result1 = SequenceOperations.findIntersections(resultList, list1, list2);
        assertArrayEquals(new int[]{2, 3}, result1.toIntArray());

        // Reuse the same result list
        resultList.clear();
        IntList list3 = IntArrayList.wrap(new int[]{5, 6, 7});
        IntList list4 = IntArrayList.wrap(new int[]{6, 7, 8});
        IntList result2 = SequenceOperations.findIntersections(resultList, list3, list4);
        assertArrayEquals(new int[]{6, 7}, result2.toIntArray());
    }

    // ========== minDistance Tests ==========

    @Test
    public void testMinDistance_EmptyArray() {
        IntList[] positions = new IntList[0];
        int result = SequenceOperations.minDistance(positions);
        assertEquals(0, result, "Empty array should return 0");
    }

    @Test
    public void testMinDistance_SingleList() {
        IntList[] positions = {IntArrayList.wrap(new int[]{1, 2, 3})};
        int result = SequenceOperations.minDistance(positions);
        assertEquals(0, result, "Single list should return 0");
    }

    @Test
    public void testMinDistance_OneEmptyList() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{1, 2, 3}),
                new IntArrayList()
        };
        int result = SequenceOperations.minDistance(positions);
        assertEquals(Integer.MAX_VALUE, result, "Empty list should return MAX_VALUE");
    }

    @Test
    public void testMinDistance_TwoListsOverlapping() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{1, 5, 10}),
                IntArrayList.wrap(new int[]{2, 6, 11})
        };
        int result = SequenceOperations.minDistance(positions);
        assertEquals(1, result, "Minimum distance should be 1 (between 5 and 6 or 10 and 11)");
    }

    @Test
    public void testMinDistance_TwoListsIdenticalElements() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{1, 5, 10}),
                IntArrayList.wrap(new int[]{1, 5, 10})
        };
        int result = SequenceOperations.minDistance(positions);
        assertEquals(0, result, "Identical elements should have distance 0");
    }

    @Test
    public void testMinDistance_TwoListsFarApart() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{1, 2, 3}),
                IntArrayList.wrap(new int[]{100, 200, 300})
        };
        int result = SequenceOperations.minDistance(positions);
        assertEquals(97, result, "Minimum distance should be 97 (between 3 and 100)");
    }

    @Test
    public void testMinDistance_ThreeLists() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{1, 5, 10}),
                IntArrayList.wrap(new int[]{3, 6, 15}),
                IntArrayList.wrap(new int[]{4, 7, 12})
        };
        int result = SequenceOperations.minDistance(positions);
        // Best set: 5, 6, 7 -> distance = 2 (7-5)
        // Or: 10, 15, 12 -> distance = 5 (15-10)
        assertEquals(2, result, "Minimum distance should be 2");
    }

    @Test
    public void testMinDistance_WithOffsets() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{1, 5, 10}),
                IntArrayList.wrap(new int[]{8, 12, 17})
        };
        int[] offsets = {0, -3};  // Second list becomes 5, 9, 14
        int result = SequenceOperations.minDistance(positions, offsets);
        assertEquals(0, result, "With offset, should find exact match at 5");
    }

    @Test
    public void testMinDistance_WithNegativeOffsets() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{10, 20, 30}),
                IntArrayList.wrap(new int[]{5, 15, 25})
        };
        int[] offsets = {-5, 0};  // First list becomes 5, 15, 25
        int result = SequenceOperations.minDistance(positions, offsets);
        assertEquals(0, result, "Negative offset should create exact matches");
    }

    @Test
    public void testMinDistance_NegativeNumbers() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{-10, -5, 0}),
                IntArrayList.wrap(new int[]{-8, -3, 2})
        };
        int result = SequenceOperations.minDistance(positions);
        assertEquals(2, result, "Should handle negative numbers correctly (distance between -5 and -3)");
    }

    @Test
    public void testMinDistance_SingleElementLists() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{5}),
                IntArrayList.wrap(new int[]{10}),
                IntArrayList.wrap(new int[]{15})
        };
        int result = SequenceOperations.minDistance(positions);
        assertEquals(10, result, "Distance with single elements should be 10 (15-5)");
    }

    @Test
    public void testMinDistance_LargeDistances() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{1, 1000000}),
                IntArrayList.wrap(new int[]{999999, 2000000})
        };
        int result = SequenceOperations.minDistance(positions);
        assertEquals(1, result, "Should find minimum distance of 1 (between 999999 and 1000000)");
    }

    @Test
    public void testMinDistance_ComplexScenario() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{1, 10, 20, 30}),
                IntArrayList.wrap(new int[]{5, 15, 25, 35}),
                IntArrayList.wrap(new int[]{8, 18, 28, 38})
        };
        int result = SequenceOperations.minDistance(positions);
        // Best combinations might be:
        // 10, 5, 8 -> distance = 5 (10-5)
        // 20, 15, 18 -> distance = 5 (20-15)
        // 20, 25, 18 -> distance = 7 (25-18)
        assertTrue(result <= 5, "Should find a small minimum distance");
    }

    @Test
    public void testMinDistance_ZeroOffsets() {
        IntList[] positions = {
                IntArrayList.wrap(new int[]{1, 5, 10}),
                IntArrayList.wrap(new int[]{2, 6, 11})
        };
        int[] offsets = {0, 0};
        int result = SequenceOperations.minDistance(positions, offsets);
        assertEquals(1, result, "Zero offsets should be same as no offsets");
    }

}
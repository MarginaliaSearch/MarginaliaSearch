package nu.marginalia.array.page;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.LongPredicate;

import static org.junit.jupiter.api.Assertions.*;

class LongQueryBufferTest {

    @Test
    void testRetainMatchOrder() {
        var buffer = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5, 6, 7, 8 }, 8);

        filter(buffer, v -> v % 2 == 0);
        buffer.finalizeFiltering();

        assertArrayEquals(new long[] { 2, 4, 6, 8 }, buffer.copyData());
    }

    @Test
    void testTryOtherSortedness() {
        var buffer = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5, 6, 7, 8 }, 8);

        filter(buffer, Set.of(3L, 5L, 8L)::contains);
        buffer.tryOther();

        assertTrue(buffer.isAscending());
        assertTrue(buffer.hasMore());
    }

    @Test
    void testMultipassUnion() {
        var buffer = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5, 6, 7, 8 }, 8);

        // An ascending merge filter would miss values that sort before ones it has already
        // passed, so scrambling between passes would lose 1 and 2 here
        filter(buffer, Set.of(3L, 5L, 8L)::contains);
        buffer.tryOther();
        mergeFilter(buffer, new long[] { 1, 2, 4 });
        buffer.tryOther();
        mergeFilter(buffer, new long[] { 6 });
        buffer.finalizeMultipass();

        assertArrayEquals(new long[] { 1, 2, 3, 4, 5, 6, 8 }, buffer.copyData());
    }

    @Test
    void testUniq() {
        var buffer = new LongQueryBuffer(new long[] { 1, 1, 2, 3, 3, 3, 4 }, 7);

        buffer.uniq();

        assertArrayEquals(new long[] { 1, 2, 3, 4 }, buffer.copyData());
    }

    private static void filter(LongQueryBuffer buffer, LongPredicate predicate) {
        while (buffer.hasMore()) {
            if (predicate.test(buffer.currentValue())) {
                buffer.retainAndAdvance();
            }
            else {
                buffer.rejectAndAdvance();
            }
        }
    }

    private static void mergeFilter(LongQueryBuffer buffer, long[] sortedValues) {
        int cursor = 0;

        while (buffer.hasMore()) {
            long value = buffer.currentValue();

            while (cursor < sortedValues.length && sortedValues[cursor] < value) {
                cursor++;
            }

            if (cursor < sortedValues.length && sortedValues[cursor] == value) {
                buffer.retainAndAdvance();
            }
            else {
                buffer.rejectAndAdvance();
            }
        }
    }
}

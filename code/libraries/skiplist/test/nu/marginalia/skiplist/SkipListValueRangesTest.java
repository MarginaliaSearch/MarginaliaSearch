package nu.marginalia.skiplist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkipListValueRangesTest {

    @Test
    public void test0() {
        var ranges = new SkipListValueRanges(new long[] { }, new long[] { });
        assertTrue(ranges.atEnd());
        assertFalse(ranges.next());
        assertTrue(ranges.atEnd());
    }

    @Test
    public void test1() {
        var ranges = new SkipListValueRanges(new long[] { 1 }, new long[] { 2 });
        assertFalse(ranges.atEnd());
        assertEquals(1, ranges.start());
        assertEquals(2, ranges.end());
        assertFalse(ranges.next());
        assertTrue(ranges.atEnd());
    }

    @Test
    public void test2() {
        var ranges = new SkipListValueRanges(new long[] { 1, 3 }, new long[] { 2, 4 });
        assertFalse(ranges.atEnd());
        assertEquals(1, ranges.start());
        assertEquals(2, ranges.end());
        assertFalse(ranges.atEnd());
        assertTrue(ranges.next());
        assertEquals(3, ranges.start());
        assertEquals(4, ranges.end());
        assertFalse(ranges.atEnd());
        assertFalse(ranges.next());
        assertTrue(ranges.atEnd());
    }
}
package nu.marginalia.memex.memex.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemexNodeHeadingIdTest {
    @Test
    public void test() {
        var heading = new MemexNodeHeadingId(0);
        assertEquals("0", heading.toString());
        assertEquals("1", heading.next(0).toString());
        assertEquals("0.1", heading.next(1).toString());
        assertEquals("0.0.1", heading.next(2).toString());
        assertEquals("0.1", heading.next(2).next(1).toString());
    }

    @Test
    public void testParenthood() {
        var heading = new MemexNodeHeadingId(1,0,2);

        assertTrue(heading.isChildOf(new MemexNodeHeadingId(1,0)));
        assertTrue(heading.isChildOf(new MemexNodeHeadingId(1)));
        assertFalse(heading.isChildOf(new MemexNodeHeadingId(1,1)));
        assertFalse(heading.isChildOf(new MemexNodeHeadingId(1,0,1)));
    }

    @Test
    public void testComparator() {
        assertTrue(new MemexNodeHeadingId(1).compareTo(new MemexNodeHeadingId(2)) < 0);
        assertTrue(new MemexNodeHeadingId(1).compareTo(new MemexNodeHeadingId(1, 1)) > 0);
    }
}
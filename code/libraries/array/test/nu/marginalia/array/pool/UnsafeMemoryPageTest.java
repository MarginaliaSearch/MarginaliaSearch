package nu.marginalia.array.pool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

class UnsafeMemoryPageTest {

    @Test
    void binarySearchLong() {
        MemorySegment ms = Arena.ofAuto().allocate(8 * 9);
        ms.setAtIndex(JAVA_LONG, 0, 2260);
        ms.setAtIndex(JAVA_LONG, 1, 2513);
        ms.setAtIndex(JAVA_LONG, 2, 3531);
        ms.setAtIndex(JAVA_LONG, 3, 4637);
        ms.setAtIndex(JAVA_LONG, 4, 4975);
        ms.setAtIndex(JAVA_LONG, 5, 6647);
        ms.setAtIndex(JAVA_LONG, 6, 7179);
        ms.setAtIndex(JAVA_LONG, 7, 7509);
        ms.setAtIndex(JAVA_LONG, 8, 8000);
        UnsafeMemoryPage page = new UnsafeMemoryPage(ms, 1);
        Assertions.assertEquals(0, page.binarySearchLong(2260, 0, 0, 9));
        Assertions.assertEquals(1, page.binarySearchLong(2513, 0, 0, 9));
        Assertions.assertEquals(2, page.binarySearchLong(3531, 0, 0, 9));
        Assertions.assertEquals(3, page.binarySearchLong(4637, 0, 0, 9));
        Assertions.assertEquals(4, page.binarySearchLong(4975, 0, 0, 9));
        Assertions.assertEquals(5, page.binarySearchLong(6647, 0, 0, 9));
        Assertions.assertEquals(6, page.binarySearchLong(7179, 0, 0, 9));
        Assertions.assertEquals(7, page.binarySearchLong(7509, 0, 0, 9));
        Assertions.assertEquals(8, page.binarySearchLong(8000, 0, 0, 9));
    }
}
package nu.marginalia.segmentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HasherGroupTest {

    @Test
    void ordered() {
        long a = 5;
        long b = 3;
        long c = 2;

        var group = HasherGroup.ordered();
        assertNotEquals(group.apply(a, b), group.apply(b, a));
        assertEquals(group.apply(b,c), group.replace(group.apply(a, b), c, a, 2));
    }

    @Test
    void unordered() {
        long a = 5;
        long b = 3;
        long c = 2;

        var group = HasherGroup.unordered();

        assertEquals(group.apply(a, b), group.apply(b, a));
        assertEquals(group.apply(b, c), group.replace(group.apply(a, b), c, a, 2));
    }


}

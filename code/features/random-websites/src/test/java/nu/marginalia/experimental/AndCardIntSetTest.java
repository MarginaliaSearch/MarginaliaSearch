package nu.marginalia.experimental;

import nu.marginalia.browse.experimental.AndCardIntSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AndCardIntSetTest {

    @Test
    public void testCardinality() {
        assertEquals(1, AndCardIntSet.of(1).getCardinality());
        assertEquals(0, AndCardIntSet.of().getCardinality());
        assertEquals(0, new AndCardIntSet().getCardinality());
        assertEquals(4,  AndCardIntSet.of(0,5,2,4).getCardinality());
    }
    @Test
    public void testAndCardinality() {
        assertEquals(1, AndCardIntSet.andCardinality(AndCardIntSet.of(1,3,5), AndCardIntSet.of(2,3,4)));
        assertEquals(4, AndCardIntSet.andCardinality(AndCardIntSet.of(1,2,3,4,5, 6), AndCardIntSet.of(1,2,3,4)));
        assertEquals(4, AndCardIntSet.andCardinality(AndCardIntSet.of(0, 1,2,3,4,5, 6), AndCardIntSet.of(1,2,3,4)));
        assertEquals(4, AndCardIntSet.andCardinality(AndCardIntSet.of(1,2,3,4,5, 6), AndCardIntSet.of(0, 1,2,3,4)));
    }

}
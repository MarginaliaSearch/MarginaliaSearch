package nu.marginalia.adjacencies;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SparseBitVectorTest {

    @Test
    public void testCardinality() {
        assertEquals(1, SparseBitVector.of(1).getCardinality());
        assertEquals(0, SparseBitVector.of().getCardinality());
        assertEquals(0, new SparseBitVector().getCardinality());
        assertEquals(4,  SparseBitVector.of(0,5,2,4).getCardinality());
    }
    @Test
    public void testAndCardinality() {
        assertEquals(1, SparseBitVector.andCardinality(SparseBitVector.of(1,3,5), SparseBitVector.of(2,3,4)));
        assertEquals(4, SparseBitVector.andCardinality(SparseBitVector.of(1,2,3,4,5, 6), SparseBitVector.of(1,2,3,4)));
        assertEquals(4, SparseBitVector.andCardinality(SparseBitVector.of(0, 1,2,3,4,5, 6), SparseBitVector.of(1,2,3,4)));
        assertEquals(4, SparseBitVector.andCardinality(SparseBitVector.of(1,2,3,4,5, 6), SparseBitVector.of(0, 1,2,3,4)));
    }

}
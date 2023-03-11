package nu.marginalia.language.statistics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DenseBitMapTest {

    @Test
    public void testSetAll() {
        var dbm = new DenseBitMap(129);
        for (int i = 0; i < dbm.cardinality; i++) {
            dbm.set(i);
        }

        for (int i = 0; i < dbm.cardinality; i++) {
            assertTrue(dbm.get(i));
        }
    }

    @Test
    public void testSetEven() {
        var dbm = new DenseBitMap(131);
        for (int i = 0; i < dbm.cardinality; i+=2) {
            dbm.set(i);
        }

        for (int i = 0; i < dbm.cardinality; i+=2) {
            assertTrue(dbm.get(i));
        }

        for (int i = 1; i < dbm.cardinality; i+=2) {
            assertFalse(dbm.get(i));
        }
    }

    @Test
    public void testSetAllClearSome() {
        var dbm = new DenseBitMap(129);

        for (int i = 0; i < dbm.cardinality; i++) {
            dbm.set(i);
        }
        for (int i = 1; i < dbm.cardinality; i+=2) {
            dbm.clear(i);
        }

        for (int i = 0; i < dbm.cardinality; i+=2) {
            assertTrue(dbm.get(i), "Expected " + i + " to be set");
        }

        for (int i = 1; i < dbm.cardinality; i+=2) {
            assertFalse(dbm.get(i), "Expected " + i + " to be clear");
        }
    }
}
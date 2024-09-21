package nu.marginalia.sequence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VarintCodedSequenceTest {

    @Test
    public void testSimple() {
        var sequence = VarintCodedSequence.generate(1, 3, 5, 16, 1024, 2048, 40000, 268435446);

        assertEquals(8, sequence.valueCount());

        var values = sequence.values();
        System.out.println(values);
        assertEquals(1, values.getInt(0));
        assertEquals(3, values.getInt(1));
        assertEquals(5, values.getInt(2));
        assertEquals(16, values.getInt(3));
        assertEquals(1024, values.getInt(4));
        assertEquals(2048, values.getInt(5));
        assertEquals(40000, values.getInt(6));
        assertEquals(268435446, values.getInt(7));


        var iter = sequence.iterator();
        assertEquals(1, iter.nextInt());
        assertEquals(3, iter.nextInt());
        assertEquals(5, iter.nextInt());
        assertEquals(16, iter.nextInt());
        assertEquals(1024, iter.nextInt());
        assertEquals(2048, iter.nextInt());
        assertEquals(40000, iter.nextInt());
        assertEquals(268435446, iter.nextInt());

    }


    @Test
    public void testSimultaneousIteration() {
        var sequence = VarintCodedSequence.generate(1, 3, 5, 16, 1024, 2048, 40000, 268435446);

        assertEquals(8, sequence.valueCount());

        var values = sequence.values();
        System.out.println(values);
        assertEquals(1, values.getInt(0));
        assertEquals(3, values.getInt(1));
        assertEquals(5, values.getInt(2));
        assertEquals(16, values.getInt(3));

        var iter1 = sequence.iterator();
        var iter2 = sequence.iterator();
        assertEquals(1, iter1.nextInt());
        assertEquals(3, iter1.nextInt());
        assertEquals(1, iter2.nextInt());
        assertEquals(3, iter2.nextInt());
    }

    @Test
    public void testEmpty() {
        var sequence = VarintCodedSequence.generate();

        assertEquals(0, sequence.valueCount());

        var values = sequence.values();
        assertTrue(values.isEmpty());

        var iter = sequence.iterator();
        assertFalse(iter.hasNext());
    }
}
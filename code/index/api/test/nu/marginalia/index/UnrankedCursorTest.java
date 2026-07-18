package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UnrankedCursorTest {

    @Test
    public void testEncodeDecodePartial() {
        IntList nodes = IntList.of(1, 5, 3);
        LongList positions = LongList.of(30L, 0x1000F000A000E000L, -3000L);

        String encoded = new UnrankedCursor.Partial(nodes, positions).encode();
        System.out.println(encoded);

        var parsed = UnrankedCursor.parse(encoded);
        System.out.println(parsed);

        Assertions.assertTrue(parsed instanceof UnrankedCursor.Partial);

        UnrankedCursor.Partial partial = (UnrankedCursor.Partial) parsed;

        Assertions.assertEquals(nodes, partial.nodes());
        Assertions.assertEquals(positions, partial.positions());
    }

    @Test
    public void testEncodeDecodeTerminal() {
        String encoded = new UnrankedCursor.Terminal().encode();
        System.out.println(encoded);
        Assertions.assertEquals("FIN", encoded);
        Assertions.assertTrue(UnrankedCursor.parse(encoded) instanceof UnrankedCursor.Terminal);
    }

    @Test
    public void testEncodeDecodeUninitialized() {
        String encoded = new UnrankedCursor.Uninitialized().encode();
        System.out.println(encoded);
        Assertions.assertEquals("", encoded);
        Assertions.assertTrue(UnrankedCursor.parse(encoded) instanceof UnrankedCursor.Uninitialized);
    }

}
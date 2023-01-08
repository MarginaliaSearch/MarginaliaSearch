package nu.marginalia.util.array.scheme;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ArrayPartitioningSchemeTest {

    @Test
    public void testPo2() {
        var p2 = new PowerOf2PartitioningScheme(64);
        var seq = new SequentialPartitioningScheme(64);

        for (int i = 0; i < 512; i++) {
            Assertions.assertEquals(p2.getPage(i), seq.getPage(i), "Unexpected buffer @ " + i);
            Assertions.assertEquals(p2.getOffset(i), seq.getOffset(i), "Unexpected offset @ " + i);
            Assertions.assertEquals(p2.isSamePage(i, i+1), seq.isSamePage(i, i+1), "Unexpected value @ " + i);
        }
    }
}
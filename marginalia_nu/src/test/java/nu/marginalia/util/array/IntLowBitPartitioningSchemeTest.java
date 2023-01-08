package nu.marginalia.util.array;

import nu.marginalia.util.array.scheme.SequentialPartitioningScheme;
import org.junit.jupiter.api.Test;

class IntLowBitPartitioningSchemeTest {

    @Test
    public void testLBPT() {
        var p = new SequentialPartitioningScheme(18);

        System.out.println(p.getRequiredPageSize(0, 51));
        System.out.println(p.getRequiredPageSize(1, 51));
        System.out.println(p.getRequiredPageSize(2, 51));
        System.out.println(p.getRequiredPageSize(3, 51));

        for (int i = 0; i < 100; i++) {
            System.out.println(p.getPage(i) + ":" + p.getOffset(i));
        }
    }
}
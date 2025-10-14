package nu.marginalia.keyword.model;

import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static nu.marginalia.keyword.model.DocumentKeywordsBuilder.POSITIONS_BITMASK_WINDOW_SIZE;

class DocumentKeywordsBuilderTest {
    DocumentKeywordsBuilder builder;

    @BeforeEach
    public void setUp() {
        builder = new DocumentKeywordsBuilder();
    }

    @Test
    void calculatePositionMask__preserveTermMeta() {
        Assertions.assertEquals(0L, builder.calculatePositionMask(0L, IntList.of(1)) & 0xFFL);
        Assertions.assertEquals(0L, builder.calculatePositionMask(0L, IntList.of(1024)) & 0xFFL);
        Assertions.assertEquals(0L, builder.calculatePositionMask(0L, IntList.of(15)) & 0xFFL);
        Assertions.assertEquals(0L, builder.calculatePositionMask(0L, IntList.of(7000)) & 0xFFL);
        Assertions.assertEquals(0L, builder.calculatePositionMask(0L, IntList.of(-1)) & 0xFFL);

        Assertions.assertEquals(40L, builder.calculatePositionMask(40L, IntList.of(1)) & 0xFFL);
        Assertions.assertEquals(40L, builder.calculatePositionMask(40L, IntList.of(1024)) & 0xFFL);
        Assertions.assertEquals(40L, builder.calculatePositionMask(40L, IntList.of(15)) & 0xFFL);
        Assertions.assertEquals(40L, builder.calculatePositionMask(40L, IntList.of(7000)) & 0xFFL);
        Assertions.assertEquals(40L, builder.calculatePositionMask(40L, IntList.of(-1)) & 0xFFL);
    }

    @Test
    void calculatePositionMask__adjacentTermsAlwaysOverlap() {
        // Invariant:
        //
        // mask() is such that for any given pair of two positions (i,i+j) where j < windowSize/2, it is true that mask(i) & mask(i+j) != 0

        for (int i = 0; i < 1000; i++) {
            for (int j = 0; j <= POSITIONS_BITMASK_WINDOW_SIZE/2; j++) {
                long maskI = builder.calculatePositionMask(0L, IntList.of(i)) >>> 8;
                long maskIJ = builder.calculatePositionMask(0L, IntList.of(i+j)) >>> 8;

                Assertions.assertNotEquals(0L, maskI & maskIJ,
                    "Position masks not overlapping at " + (i + ", " + (i+j)) + ": " + Long.toHexString(maskI) + " vs " + Long.toHexString(maskIJ)
                );
            }
        }
    }


    @Test
    void calculatePositionMask__verifyFullRangeOfBitsUsed() {
        long totalMask = 0L;

        for (int i = 0; i < 8000; i++) {
            totalMask |= builder.calculatePositionMask(0L, IntList.of(i));
        }

        Assertions.assertEquals(0xFFFF_FFFF_FFFF_FF00L, totalMask);
    }
}
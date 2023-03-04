package nu.marginalia.util;

import org.junit.jupiter.api.Test;

class BrailleBlockPunchCardsTest {
    @Test
    public void test() {
        for (int i = 0; i <= 512; i++) {
            if ((i % 8) == 0) {
                System.out.println();
            }
            System.out.print(BrailleBlockPunchCards.printBits(i, 8));

        }
    }

}
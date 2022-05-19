package nu.marginalia.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SeekDictionaryTest {

    @Test
    public void testSeek() {
        var dict = SeekDictionary.of((int[] x) -> x.length);

        for (int i = 0; i < 10000;) {
            int j = (int)(1 + 9 * Math.random());
            int[] block = new int[j];
            for (int k = 0; k < j; k++) {
                block[k] = i+k;
            }
            dict.add(block);
            i+=j;
        }

        o: for (int i = 0; i < 10000; i++) {
            int[] vals = dict.bankForOffset(i);
            for (var v : vals) {
                if (v == i) continue o;
            }
            Assertions.fail("Could not find " + i);
        }
    }

}
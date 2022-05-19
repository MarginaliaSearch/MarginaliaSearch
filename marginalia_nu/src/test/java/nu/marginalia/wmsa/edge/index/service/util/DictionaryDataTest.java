package nu.marginalia.wmsa.edge.index.service.util;

import nu.marginalia.util.dict.DictionaryData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DictionaryDataTest {

    @Test
    public void testDataBankGrow2() {
        var dataBank = new DictionaryData(65535);
        for (int i = 0; i < 64; i++) {
            String s = "" + i;
            int offset = dataBank.add(s.getBytes(), i);
            System.out.println(s + " " + offset + " " + new String(dataBank.getBytes(i)) + " " + dataBank.getValue(i));

            Assertions.assertEquals(s, new String(dataBank.getBytes(i)));
            Assertions.assertEquals(i, dataBank.getValue(i));
        }
    }
}
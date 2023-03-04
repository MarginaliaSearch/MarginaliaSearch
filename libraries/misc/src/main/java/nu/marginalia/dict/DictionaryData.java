package nu.marginalia.dict;

import java.util.ArrayList;

public class DictionaryData {
    private final int bankSize;

    private final ArrayList<DictionaryDataBank> banks = new ArrayList<>(100);

    public DictionaryData(int bankSize) {
        this.bankSize = bankSize;

        banks.add(new DictionaryDataBank(0, bankSize));
    }

    public int add(long key) {
        var activeBank = banks.get(banks.size()-1);
        int rb = activeBank.add(key);

        if (rb == -1) {
            int end = activeBank.getEnd();
            var newBank = new DictionaryDataBank(end, bankSize);
            rb = newBank.add(key);

            banks.add(newBank);
        }

        return rb;
    }


    public long getKey(int offset) {
        return banks.get(offset/ bankSize).getKey(offset);
    }
    public boolean keyEquals(int offset, long otherKey) {
        return banks.get(offset/ bankSize).keyEquals(offset, otherKey);
    }

}

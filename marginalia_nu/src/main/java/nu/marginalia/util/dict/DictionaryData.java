package nu.marginalia.util.dict;

import nu.marginalia.util.SeekDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class DictionaryData {

    private final int DICTIONARY_BANK_SIZE;
    private static final Logger logger = LoggerFactory.getLogger(DictionaryData.class);

    private final SeekDictionary<DictionaryDataBank> banks = SeekDictionary.of(DictionaryDataBank::getSize);

    public DictionaryData(int bankSize) {
        DICTIONARY_BANK_SIZE = bankSize;

        banks.add(new DictionaryDataBank(0));
    }

    public int size() {
        return banks.end();
    }

    public int add(byte[] data, int value) {
        var activeBank = banks.last();
        int rb = activeBank.add(data, value);

        if (rb == -1) {
            int end = activeBank.getEnd();
            logger.debug("Switching bank @ {}", end);
            var newBank = new DictionaryDataBank(end);
            rb = newBank.add(data, value);

            banks.add(newBank);
        }

        return rb;
    }


    public byte[] getBytes(int offset) {
        return banks.bankForOffset(offset).getBytes(offset);
    }
    public boolean keyEquals(int offset, byte[] data) {
        return banks.bankForOffset(offset).keyEquals(offset, data);
    }

    public int getValue(int offset) {
        return banks.bankForOffset(offset).getValue(offset);
    }

    public class DictionaryDataBank {

        private final int start_idx;
        private final ByteBuffer data;

        private int size;
        private int[] offset;
        private int[] value;

        public DictionaryDataBank(int start_idx) {
            this.start_idx = start_idx;

            data = ByteBuffer.allocateDirect(DICTIONARY_BANK_SIZE);

            offset = new int[DICTIONARY_BANK_SIZE/16];
            value = new int[DICTIONARY_BANK_SIZE/16];
            size = 0;
        }

        public int getStart() {
            return start_idx;
        }

        public int getEnd() {
            return start_idx + size;
        }

        public byte[] getBytes(int idx) {
            if (idx < start_idx || idx - start_idx >= size) {
                throw new IndexOutOfBoundsException(idx);
            }

            idx = idx - start_idx;

            final int start;
            final int end = offset[idx];

            if (idx == 0) start = 0;
            else start = offset[idx-1];

            byte[] dst = new byte[end-start];
            data.get(start, dst);
            return dst;
        }

        public int getValue(int idx) {
            if (idx < start_idx || idx - start_idx >= size) {
                throw new IndexOutOfBoundsException(idx);
            }
            return value[idx - start_idx];
        }

        public boolean keyEquals(int idx, byte[] data) {
            if (idx < start_idx || idx - start_idx >= size) {
                throw new IndexOutOfBoundsException(idx);
            }

            idx = idx - start_idx;
            int start;
            int end = offset[idx];

            if (idx == 0) {
                start = 0;
            }
            else {
                start = offset[idx-1];
            }
            if (data.length != end - start) {
                return false;
            }
            for (int i = 0; i < data.length; i++) {
                if (this.data.get(start + i) != data[i]) {
                    return false;
                }
            }
            return true;
        }

        public long longHashCode(int idx) {
            if (idx < start_idx || idx - start_idx >= size) {
                throw new IndexOutOfBoundsException(idx);
            }

            idx = idx - start_idx;
            int start;
            int end = offset[idx];

            if (idx == 0) {
                start = 0;
            }
            else {
                start = offset[idx-1];
            }

            long result = 1;
            for (int i = start; i < end; i++)
                result = 31 * result + data.get(i);

            return result;
        }

        public int add(byte[] newData, int newValue) {
            if (size == offset.length) {
                logger.debug("Growing bank from {} to {}", offset.length, offset.length*2);
                offset = Arrays.copyOf(offset, offset.length*2);
                value = Arrays.copyOf(value, value.length*2);
            }

            if (size > 0 && offset[size-1]+newData.length >= DICTIONARY_BANK_SIZE) {
                if (offset.length > size+1) {
                    logger.debug("Shrinking bank from {} to {}", offset.length, size - 1);
                    offset = Arrays.copyOf(offset, size + 1);
                    value = Arrays.copyOf(value, size + 1);
                }
                return -1; // Full
            }

            int dataOffset = size > 0 ? offset[size-1] : 0;

            data.put(dataOffset, newData);

            offset[size] = dataOffset + newData.length;
            value[size] = newValue;

            return start_idx + size++;
        }

        public int getSize() {
            return size;
        }
    }
}

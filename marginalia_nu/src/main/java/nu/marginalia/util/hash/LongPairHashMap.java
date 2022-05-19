package nu.marginalia.util.hash;

import io.prometheus.client.Gauge;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nu.marginalia.wmsa.edge.index.service.index.wordstable.IndexWordsTable;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.util.PrimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Math.round;

/**
 *  Spiritually influenced by GNU Trove's hash maps
 *  LGPL 2.1
 */
public class LongPairHashMap {
    private static final Logger logger = LoggerFactory.getLogger(LongPairHashMap.class);
    private static final Gauge probe_count_metrics
            = Gauge.build("wmsa_wordfile_hash_map_probe_count", "Probing Count")
            .register();

    private final long hashTableSize;
    private final MultimapFileLong data;
    private final long maxProbeLength;
    private int sz = 0;
    private static final int HEADER_SIZE = 2;

    public LongPairHashMap(MultimapFileLong data, long size) {
        this.data = data;
        // Actually use a prime size for Donald Knuth reasons
        hashTableSize = PrimeUtil.nextPrime(size, 1);
        maxProbeLength = hashTableSize / 2;

        logger.debug("Table size = " + hashTableSize);

        data.put(0, IndexWordsTable.Strategy.HASH.ordinal());
        data.put(1, hashTableSize);
        for (int i = 2; i < hashTableSize; i++) {
            data.put(HEADER_SIZE + 2L*i, 0);
        }
    }
    public LongPairHashMap(MultimapFileLong data) {
        this.data = data;
        hashTableSize = data.get(1);
        maxProbeLength = hashTableSize / 10;

        logger.debug("Table size = " + hashTableSize);
    }

    public int size() {
        return sz;
    }

    private CellData getCell(long idx) {
        long bufferIdx = 2*idx + HEADER_SIZE;
        long a = data.get(bufferIdx);
        long b = data.get(bufferIdx+1);
        return new CellData(a, b);
    }
    private void setCell(long idx, CellData cell) {
        long bufferIdx = 2*idx + HEADER_SIZE;
        data.put(bufferIdx, cell.first);
        data.put(bufferIdx+1, cell.second);
    }

    public CellData put(CellData data) {

        long hash = longHash(data.getKey()) & 0x7FFF_FFFFL;

        long idx = hash% hashTableSize;
        if (!getCell(hash% hashTableSize).isSet()) {
            return setValue(data, hash% hashTableSize);
        }

        return putRehash(data, idx, hash);

    }

    private CellData putRehash(CellData data, long idx, long hash) {
        final long pStride = 1 + (hash % (hashTableSize - 2));

        for (long j = 1; j < maxProbeLength; j++) {
            idx = idx - pStride;

            if (idx < 0) {
                idx += hashTableSize;
            }

            final var val = getCell(idx);

            if (!val.isSet()) {
                probe_count_metrics.set(j);

                return setValue(data, idx);
            }
            else if (val.getKey() == data.getKey()) {
                logger.error("Double write?");
                return val;
            }
        }

        throw new IllegalStateException("DictionaryHashMap full @ size " + size() + "/" + hashTableSize + ", " + round((100.0*size()) / hashTableSize) + "%, key = " + data.getKey() + ",#"+hash);
    }

    private CellData setValue(CellData data, long cell) {
        sz++;

        setCell(cell, data);
        return data;
    }

    public CellData get(int key) {
        if (hashTableSize == 0) {
            return new CellData(0, 0);
        }
        final long hash = longHash(key) & 0x7FFF_FFFFL;

        var val = getCell(hash % hashTableSize);
        if (!val.isSet()) {
            return val;
        }
        else if (val.getKey() == key) {
            return val;
        }

        return getRehash(key, hash % hashTableSize, hash);
    }

    private CellData getRehash(int key, long idx, long hash) {
        final long pStride = 1 + (hash % (hashTableSize - 2));

        for (long j = 1; j < maxProbeLength; j++) {
            idx = idx - pStride;

            if (idx < 0) {
                idx += hashTableSize;
            }

            final var val = getCell(idx);

            if (!val.isSet()) {
                return val;
            }
            else if (val.getKey() == key) {
                return val;
            }
        }

        throw new IllegalStateException("DictionaryHashMap full @ size " + size() + "/" + hashTableSize + ", " + round((100.0*size()) / hashTableSize) + "%");
    }

    private long longHash(long x) {
        return x;
    }

    @Getter @EqualsAndHashCode
    public static class CellData {
        final long first;
        final long second;

        public CellData(long key, long offset) {
            first = key | 0x8000_0000_000_000L;
            second = offset;
        }

        public long getKey() {
            return first & ~0x8000_0000_000_000L;
        }
        public long getOffset() {
            return second;
        }

        public boolean isSet() {
            return first != 0 || second != 0L;
        }
    }

    public void close() throws Exception {
        data.close();
    }
}

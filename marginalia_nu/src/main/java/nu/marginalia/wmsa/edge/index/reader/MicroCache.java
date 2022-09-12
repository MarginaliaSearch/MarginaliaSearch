package nu.marginalia.wmsa.edge.index.reader;

import java.util.Arrays;

public class MicroCache {
    private final int[] keys;
    private final long[] data;
    private int pos = 0;

    public int hit;
    public int miss;
    public int full;

    public static final long BAD_VALUE = Long.MIN_VALUE;

    public MicroCache(int size) {
        keys = new int[size];
        data = new long[size];

        Arrays.fill(data, BAD_VALUE);
    }

    public long get(int key) {
        for (int i = 0; i < keys.length && data[i] != BAD_VALUE; i++) {
            if (keys[i] == key) {
                hit++;
                return data[i];
            }
        }
        miss++;
        return BAD_VALUE;
    }

    public void set(int key, long val) {
        keys[pos] = key;
        data[pos] = val;

        if (++pos >= keys.length) {
            full++;
            pos = 0;
        }
    }
}

package nu.marginalia.util;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.Arrays;
import java.util.HashMap;

public class StringPool {

    private final HashMap<String, String> words;
    private final Object2LongOpenHashMap<String> ages;
    private final int maxCap;

    long idx;

    private StringPool(int capacity, int maxCap) {
        this.ages  = new Object2LongOpenHashMap<>(capacity);
        this.words = new HashMap<>(capacity);
        this.maxCap = maxCap;
    }

    public static StringPool create(int capacity) {
        return new StringPool(capacity, capacity * 10);
    }

    public String internalize(String str) {
        prune();

        final String ret = words.putIfAbsent(str, str);
        ages.put(ret, idx++);

        if (null == ret)
            return str;

        return ret;
    }

    public String[] internalize(String[] str) {

        for (int i = 0; i < str.length; i++) {
            str[i] = internalize(str[i]);
        }

        return str;
    }

    public void prune() {

        if (words.size() < maxCap)
            return;

        long[] ageValues = ages.values().toLongArray();
        Arrays.sort(ageValues);

        long cutoff = ageValues[ageValues.length - maxCap / 10];

        words.clear();
        ages.forEach((word, cnt) -> {
            if (cnt >= cutoff) {
                words.put(word, word);
            }
        });
        ages.clear();
        words.forEach((w,w2) -> {
            ages.put(w, idx);
        });
    }

    public void flush() {
        words.clear();
    }
}

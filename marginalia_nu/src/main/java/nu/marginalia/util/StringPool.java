package nu.marginalia.util;

import java.util.HashMap;

public class StringPool {
    private final HashMap<String, String> words;

    public StringPool() {
        this.words  = new HashMap<>(1000);
    }

    public StringPool(int capacity) {
        words = new HashMap<>(capacity);
    }

    public String internalize(String str) {
        final String ret = words.putIfAbsent(str, str);

        if (null == ret)
            return str;

        return ret;
    }

    public void flush() {
        words.clear();
    }
}

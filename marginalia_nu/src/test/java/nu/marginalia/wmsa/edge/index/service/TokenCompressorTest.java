package nu.marginalia.wmsa.edge.index.service;

import nu.marginalia.wmsa.edge.index.service.dictionary.TokenCompressor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class TokenCompressorTest {

    @Test
    public void getWordBytes() {
        final Map<String, Integer> map = new HashMap<>();
        TokenCompressor tc = new TokenCompressor(word -> {
            map.put(word, map.size());
            return map.size()-1;
        });

        System.out.println(Arrays.toString(tc.getWordBytes("308")));
        System.out.println(Arrays.toString(tc.getWordBytes(".308")));
        System.out.println(Arrays.toString(tc.getWordBytes("308.")));
        System.out.println(Arrays.toString(tc.getWordBytes("30.8.")));
        System.out.println(Arrays.toString(tc.getWordBytes("30...")));

        map.entrySet().forEach(System.out::println);
    }
}
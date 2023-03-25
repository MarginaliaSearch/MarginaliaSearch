package nu.marginalia.term_frequency_dict;

import ca.rmen.porterstemmer.PorterStemmer;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import lombok.SneakyThrows;
import nu.marginalia.LanguageModels;
import nu.marginalia.array.LongArray;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dictionary with term frequency information for (stemmed) words.
 *
 */
@Singleton
public class TermFrequencyDict {
    private final Long2IntOpenHashMap wordRates;
    private static final Logger logger = LoggerFactory.getLogger(TermFrequencyDict.class);
    private static final PorterStemmer ps = new PorterStemmer();

    public static final long DOC_COUNT_KEY = ~0L;

    @Inject
    public TermFrequencyDict(@NotNull LanguageModels models) {
        this(models.termFrequencies);
    }

    @SneakyThrows
    public TermFrequencyDict(Path file) {

        wordRates = load(file);
        logger.info("Read {} N-grams frequencies", wordRates.size());
    }

    private static Long2IntOpenHashMap load(Path file) throws IOException {
        LongArray array = LongArray.mmapRead(file);

        int size = (int) Files.size(file)/16;
        var ret = new Long2IntOpenHashMap(size, 0.5f);

        ret.defaultReturnValue(0);

        for (int i = 0; i < size; i++) {
            ret.put(array.get(2*i), (int) array.get(2*i + 1));
        }

        return ret;
    }

    /** Total number of documents in the corpus */
    public int docCount() {
        int cnt = wordRates.get(DOC_COUNT_KEY);

        if (cnt == 0) {
            cnt = 11820118; // legacy
        }
        return cnt;
    }

    /** Get the term frequency for the string s */
    public int getTermFreq(String s) {
        return wordRates.get(getStringHash(s));
    }

    /** Get the term frequency for the already stemmed string s */
    public int getTermFreqStemmed(String s) {
        return wordRates.get(longHash(s.getBytes()));
    }

    /** Get the term frequency for the already stemmed and already hashed value 'hash' */
    public int getTermFreqHash(long hash) {
        return wordRates.get(hash);
    }

    public static long getStringHash(String s) {
        if (s.indexOf(' ') >= 0 || s.indexOf('_') >= 0) {
            String[] strings = StringUtils.split(s, " _");
            byte[][] parts = new byte[strings.length][];
            for (int i = 0; i < parts.length; i++) {
                parts[i] = ps.stemWord(strings[i]).getBytes();
            }
            return longHash(parts);
        }
        else {
            return longHash(s.getBytes());
        }
    }

    /** The hashing function used by TermFrequencyHash
     * <p>
     * If this function changes its behavior in any way,
     * it is necessary to re-generate the dictionary.
     */
    public static long longHash(byte[]... bytesSets) {
        if (bytesSets == null || bytesSets.length == 0)
            return 0;

        // https://cp-algorithms.com/string/string-hashing.html
        int p = 127;
        long m = (1L<<61)-1;
        long p_power = 1;
        long hash_val = 0;

        for (byte[] bytes: bytesSets) {
            for (byte element : bytes) {
                hash_val = (hash_val + (element + 1) * p_power) % m;
                p_power = (p_power * p) % m;
            }
        }
        return hash_val;
    }

}

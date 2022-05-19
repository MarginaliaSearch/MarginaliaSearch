package nu.marginalia.wmsa.edge.assistant.dict;

import ca.rmen.porterstemmer.PorterStemmer;
import gnu.trove.map.hash.TLongIntHashMap;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class NGramDict {

    private final TLongIntHashMap wordRates = new TLongIntHashMap(1_000_000, 0.5f, 0, 0);

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final Pattern separator = Pattern.compile("[_ ]+");
    private static final PorterStemmer ps = new PorterStemmer();

    private static long fileSize(Path p) throws IOException {
        return Files.size(p);
    }

    @Inject
    public NGramDict(@Nullable LanguageModels models) {
        if (models == null) {
            return;
        }

        if (models.ngramFrequency != null) {

            try (var frequencyData = new DataInputStream(new BufferedInputStream(new FileInputStream(models.ngramFrequency.toFile())))) {

                wordRates.ensureCapacity((int)(fileSize(models.ngramFrequency)/16));

                for (;;) {
                    wordRates.put(frequencyData.readLong(), (int) frequencyData.readLong());
                }
            } catch (EOFException eof) {
                // ok
            } catch (IOException e) {
                logger.error("IO Exception reading " + models.ngramFrequency, e);
            }
        }

        logger.info("Read {} N-grams frequencies", wordRates.size());
    }


    public static void main(String... args) {
        if (args.length != 2) {
            System.err.println("Expected arguments: in-file out-file");
        }
        String inFile = args[0];
        String outFile = args[1];

        var wordPattern = Pattern.compile("\\w+(_\\w+)*").asMatchPredicate();
        try (var linesStr = Files.lines(Path.of(inFile));
             var dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)))
        ) {
            linesStr
                    .filter(wordPattern)
                    .mapToLong(NGramDict::getStringHash).forEach(l ->
            {
                try {
                    dos.writeLong(l);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static long getStringHash(String s) {
        String[] strings = separator.split(s);
        if (s.length() > 1) {
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
    public long getTermFreqHash(long hash) {
        return wordRates.get(hash);
    }
    public long getTermFreq(String s) {
        return wordRates.get(getStringHash(s));
    }
    public long getTermFreqStemmed(String s) {
        return wordRates.get(longHash(s.getBytes()));
    }

    public static String getStemmedString(String s) {
        String[] strings = separator.split(s);
        if (s.length() > 1) {
            return Arrays.stream(strings).map(ps::stemWord).collect(Collectors.joining("_"));
        }
        else {
            return s;
        }

    }

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

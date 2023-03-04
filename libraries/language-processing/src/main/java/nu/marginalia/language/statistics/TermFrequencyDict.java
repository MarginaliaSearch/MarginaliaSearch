package nu.marginalia.language.statistics;

import ca.rmen.porterstemmer.PorterStemmer;
import gnu.trove.map.hash.TLongIntHashMap;
import nu.marginalia.LanguageModels;
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
public class TermFrequencyDict {

    private final TLongIntHashMap wordRates = new TLongIntHashMap(1_000_000, 0.5f, 0, 0);

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final Pattern separator = Pattern.compile("[_ ]+");
    private static final PorterStemmer ps = new PorterStemmer();

    private static final long DOC_COUNT_KEY = ~0L;
    private static long fileSize(Path p) throws IOException {
        return Files.size(p);
    }

    @Inject
    public TermFrequencyDict(@Nullable LanguageModels models) {
        if (models == null) {
            return;
        }

        if (models.termFrequencies != null) {

            try (var frequencyData = new DataInputStream(new BufferedInputStream(new FileInputStream(models.termFrequencies.toFile())))) {

                wordRates.ensureCapacity((int)(fileSize(models.termFrequencies)/16));

                for (;;) {
                    wordRates.put(frequencyData.readLong(), (int) frequencyData.readLong());
                }
            } catch (EOFException eof) {
                // ok
            } catch (IOException e) {
                logger.error("IO Exception reading " + models.termFrequencies, e);
            }
        }

        logger.info("Read {} N-grams frequencies", wordRates.size());
    }


    public int docCount() {
        int cnt = wordRates.get(DOC_COUNT_KEY);

        if (cnt == 0) {
            cnt = 11820118; // legacy
        }
        return cnt;
    }

//
//    public static void main(String... args) throws IOException, InterruptedException {
//        if (args.length != 2) {
//            System.err.println("Expected arguments: plan.yaml out-file");
//        }
//        String outFile = args[1];
//
//        var plan = new CrawlPlanLoader().load(Path.of(args[0]));
//
//        ThreadLocal<SentenceExtractor> se = ThreadLocal.withInitial(() -> new SentenceExtractor(WmsaHome.getLanguageModels()));
//        LanguageFilter lf = new LanguageFilter();
//
//        TLongIntHashMap counts = new TLongIntHashMap(100_000_000, 0.7f, -1, -1);
//
//        ForkJoinPool fjp = new ForkJoinPool(24);
//        AtomicInteger docCount = new AtomicInteger();
//
//        for (var domain : plan.domainsIterable()) { // leaks file descriptor, is fine
//
//            if (domain.doc == null)
//                continue;
//
//            fjp.execute(() -> {
//
//                TLongHashSet words = new TLongHashSet(10_000);
//
//                for (var doc : domain.doc) {
//
//                    if (doc.documentBody == null)
//                        continue;
//                    docCount.incrementAndGet();
//
//                    Document parsed = Jsoup.parse(doc.documentBody.decode());
//                    parsed.body().filter(new DomPruningFilter(0.5));
//
//                    DocumentLanguageData dld = se.get().extractSentences(parsed);
//
//                    if (lf.dictionaryAgreement(dld) < 0.1) {
//                        return;
//                    }
//
//                    for (var sent : dld.sentences) {
//                        for (var word : sent) {
//                            words.add(longHash(word.stemmed().getBytes(StandardCharsets.UTF_8)));
//                        }
//                    }
//
//                    synchronized (counts) {
//                        words.forEach(w -> {
//                            counts.adjustOrPutValue(w,  1, 1);
//                            return true;
//                        });
//                    }
//
//                    words.clear();
//                }
//
//                System.out.println(domain.domain + "\t" + counts.size());
//            });
//
//
//        }
//
//        fjp.shutdown();
//        fjp.awaitTermination(10, TimeUnit.DAYS);
//
//        try (var dos = new DataOutputStream(Files.newOutputStream(Path.of(outFile)))) {
//            synchronized (counts) {
//                counts.put(DOC_COUNT_KEY, docCount.get());
//
//                counts.forEachEntry((hash, cnt) -> {
//                    try {
//                        dos.writeLong(hash);
//                        dos.writeLong(cnt);
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                    return true;
//                });
//            }
//        }
//
//        System.out.println(docCount.get());
//    }

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

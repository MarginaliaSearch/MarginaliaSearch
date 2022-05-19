package nu.marginalia.wmsa.edge.tools;


import gnu.trove.set.hash.TLongHashSet;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.archive.archiver.ArchiveExtractor;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.KeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.model.crawl.EdgeRawPageContents;
import opennlp.tools.stemmer.PorterStemmer;
import org.jsoup.Jsoup;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class TermFrequencyCounterMain {

    static LinkedBlockingQueue<EdgeRawPageContents> processQueue = new LinkedBlockingQueue<>(20);

    public static final String OUTPUT_FILE = "/var/lib/wmsa/archive/tfreq-2022-04-04.bin";
    public static final String ARCHIVE_PATH = "/var/lib/wmsa/archive/webpage"; // "/mnt/storage/wmsa/archive/webpage/"

    @SneakyThrows
    public static void main(String... args) throws IOException {

        List<Thread> pt = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            pt.add(new Thread(TermFrequencyCounterMain::processorThread));
        }
        pt.forEach(Thread::start);

        AtomicLong docsTotal = new AtomicLong();
        new ArchiveExtractor(Path.of(ARCHIVE_PATH)).forEach(
                page -> {
                    if (page.contentType.contentType.contains("html")
                    && page.isAfter("2022-03-15T")) {
                        try {
                            long dt = docsTotal.incrementAndGet();
                            if (dt == 0) {
                                System.out.println(docsTotal.get() + " - " + termFreq.size());
                            }
                            if ((dt % 5) != 0) {
                                processQueue.put(page);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
        running = false;


        System.out.println("Waiting for wrap-up");

        Thread.sleep(36000);

        for (Thread thread : pt) {
            thread.interrupt();
        }
        for (Thread thread : pt) {
            thread.join();
        }
        System.out.println("Total documents = " + docsTotal.get());

        System.out.println("Writing Frequencies");

        try (var dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(OUTPUT_FILE)))
        ) {
            synchronized (termFreq) {
                for (var entry : termFreq.entrySet()) {

                    if (entry.getValue() > 5) {
                        dos.writeLong(entry.getKey());
                        dos.writeLong(entry.getValue());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        System.out.println("All done!");
    }

    public static final ConcurrentHashMap<Long, Integer> termFreq  = new ConcurrentHashMap<>();

    public static final LanguageModels lm = new LanguageModels(
            Path.of("/var/lib/wmsa/model/ngrams-generous-emstr.bin"),
            Path.of("/var/lib/wmsa/model/tfreq-generous-emstr.bin"),
            Path.of("/var/lib/wmsa/model/opennlp-sentence.bin"),
            Path.of("/var/lib/wmsa/model/English.RDR"),
            Path.of("/var/lib/wmsa/model/English.DICT"),
            Path.of("/var/lib/wmsa/model/opennlp-tok.bin")
        );
    public static volatile boolean running = true;

    public static void processorThread() {
        var ke = new KeywordExtractor();
        var se = new SentenceExtractor(lm);
        var ps = new PorterStemmer();
        try {
            TLongHashSet words = new TLongHashSet(10000);
            while (running || !processQueue.isEmpty()) {
                var job = processQueue.take();
                var sentence = se.extractSentences(Jsoup.parse(job.data));

                for (var sent : sentence.sentences) {
                    var keywords = ke.getKeywordsFromSentence(sent);
                    for (int i = 0; i < keywords.length; i++) {
                        if (keywords[i].size() > 1) {
                            words.add(NGramDict.longHash(sent.constructStemmedWordFromSpan(keywords[i]).getBytes()));
                        }
                    }

                    for (String word : sent.wordsLowerCase) {
                        words.add(NGramDict.longHash(ps.stem(word).getBytes()));
                    }

                    words.forEach(l -> {
                        termFreq.merge(l, 1, Integer::sum);
                        return true;
                    });
                    words.clear();
                }
            }
        }
        catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

}

package nu.marginalia.tools;

import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.WmsaHome;
import nu.marginalia.converting.language.LanguageFilter;
import nu.marginalia.converting.processor.logic.dom.DomPruningFilter;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.sentence.SentenceExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import plan.CrawlPlanLoader;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static nu.marginalia.term_frequency_dict.TermFrequencyDict.DOC_COUNT_KEY;
import static nu.marginalia.term_frequency_dict.TermFrequencyDict.longHash;

public class TermFrequencyExtractor {

    public static void main(String... args) throws IOException, InterruptedException {
        if (args.length != 2) {
            System.err.println("Expected arguments: plan.yaml out-file");
            return;
        }

        String outFile = args[1];

        var plan = new CrawlPlanLoader().load(Path.of(args[0]));

        ThreadLocal<SentenceExtractor> se = ThreadLocal.withInitial(() -> new SentenceExtractor(WmsaHome.getLanguageModels()));
        LanguageFilter lf = new LanguageFilter();

        TLongIntHashMap counts = new TLongIntHashMap(100_000_000, 0.7f, -1, -1);

        ForkJoinPool fjp = new ForkJoinPool(24);
        AtomicInteger docCount = new AtomicInteger();

        for (var domain : plan.domainsIterable()) { // leaks file descriptor, is fine

            if (domain.doc == null)
                continue;

            fjp.execute(() -> {

                TLongHashSet words = new TLongHashSet(10_000);

                for (var doc : domain.doc) {

                    if (doc.documentBody == null)
                        continue;
                    docCount.incrementAndGet();

                    Document parsed = Jsoup.parse(doc.documentBody.decode());
                    parsed.body().filter(new DomPruningFilter(0.5));

                    DocumentLanguageData dld = se.get().extractSentences(parsed);

                    if (lf.dictionaryAgreement(dld) < 0.1) {
                        return;
                    }

                    for (var sent : dld.sentences) {
                        for (var word : sent) {
                            words.add(longHash(word.stemmed().getBytes(StandardCharsets.UTF_8)));
                        }
                    }

                    synchronized (counts) {
                        words.forEach(w -> {
                            counts.adjustOrPutValue(w,  1, 1);
                            return true;
                        });
                    }

                    words.clear();
                }

                System.out.println(domain.domain + "\t" + counts.size());
            });


        }

        fjp.shutdown();
        fjp.awaitTermination(10, TimeUnit.DAYS);

        try (var dos = new DataOutputStream(Files.newOutputStream(Path.of(outFile)))) {
            synchronized (counts) {
                counts.put(DOC_COUNT_KEY, docCount.get());

                counts.forEachEntry((hash, cnt) -> {
                    try {
                        dos.writeLong(hash);
                        dos.writeLong(cnt);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                });
            }
        }

        System.out.println(docCount.get());
    }

}

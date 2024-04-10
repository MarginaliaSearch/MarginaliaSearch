package nu.marginalia.extractor;

import com.google.inject.Inject;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.WmsaHome;
import nu.marginalia.converting.processor.logic.dom.DomPruningFilter;
import nu.marginalia.crawling.io.CrawledDomainReader;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.language.filter.LanguageFilter;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static nu.marginalia.term_frequency_dict.TermFrequencyDict.DOC_COUNT_KEY;
import static nu.marginalia.term_frequency_dict.TermFrequencyDict.longHash;

public class TermFrequencyExporter implements ExporterIf {
    private final FileStorageService storageService;
    private final LanguageFilter lf = new LanguageFilter(WmsaHome.getLanguageModels());
    private static final Logger logger = LoggerFactory.getLogger(TermFrequencyExporter.class);

    @Inject
    public TermFrequencyExporter(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void export(FileStorageId crawlId, FileStorageId destId) throws Exception {
        Path inputDir = storageService.getStorage(crawlId).asPath();
        FileStorage destStorage = storageService.getStorage(destId);

        ThreadLocal<SentenceExtractor> se = ThreadLocal.withInitial(() -> new SentenceExtractor(WmsaHome.getLanguageModels()));

        TLongIntHashMap counts = new TLongIntHashMap(100_000_000, 0.7f, -1, -1);
        AtomicInteger docCount = new AtomicInteger();

        try (ForkJoinPool fjp = new ForkJoinPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2))) {

            Path crawlerLogFile = inputDir.resolve("crawler.log");

            for (var item : WorkLog.iterable(crawlerLogFile)) {
                if (Thread.interrupted()) {
                    fjp.shutdownNow();

                    throw new InterruptedException();
                }

                Path crawlDataPath = inputDir.resolve(item.relPath());
                fjp.execute(() -> processFile(crawlDataPath, counts, docCount, se.get()));
            }

            while (!fjp.isQuiescent()) {
                if (fjp.awaitQuiescence(10, TimeUnit.SECONDS))
                    break;
            }
        }

        var tmpFile = Files.createTempFile(destStorage.asPath(), "freqs", ".dat.tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));

        try (var dos = new DataOutputStream(Files.newOutputStream(tmpFile))) {
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
            Files.move(tmpFile, destStorage.asPath().resolve("freqs.dat"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception ex) {
            logger.error("Error writing file {}", tmpFile, ex);
            Files.deleteIfExists(tmpFile);
        }

    }

    private void processFile(Path crawlDataPath, TLongIntHashMap counts, AtomicInteger docCount, SentenceExtractor se) {
        TLongHashSet words = new TLongHashSet(10_000);
        try (var stream = CrawledDomainReader.createDataStream(CrawledDomainReader.CompatibilityLevel.FAST, crawlDataPath)) {
            while (stream.hasNext()) {
                if (Thread.interrupted())
                    return;

                if (!(stream.next() instanceof CrawledDocument doc)) continue;
                if (doc.documentBody == null) continue;
                if (!doc.contentType.startsWith("text/html"))
                    continue;

                docCount.incrementAndGet();

                Document parsed = Jsoup.parse(doc.documentBody);
                parsed.body().filter(new DomPruningFilter(0.5));

                DocumentLanguageData dld = se.extractSentences(parsed);

                if (lf.dictionaryAgreement(dld) < 0.1) {
                    return;
                }

                for (var sent : dld.sentences) {
                    for (var word : sent) {
                        words.add(longHash(word.stemmed().getBytes(StandardCharsets.UTF_8)));
                    }

                    for (var ngram : sent.ngramStemmed) {
                        words.add(longHash(ngram.getBytes()));
                    }
                }

                synchronized (counts) {
                    words.forEach(w -> {
                        counts.adjustOrPutValue(w, 1, 1);
                        return true;
                    });
                }

                words.clear();
            }
        }
        catch (Exception ex) {
            logger.error("Error processing file {}", crawlDataPath, ex);
        }
    }

}

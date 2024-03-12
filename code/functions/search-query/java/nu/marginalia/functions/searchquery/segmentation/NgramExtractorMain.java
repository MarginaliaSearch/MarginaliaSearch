package nu.marginalia.functions.searchquery.segmentation;

import it.unimi.dsi.fastutil.longs.*;
import nu.marginalia.hash.MurmurHash3_128;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openzim.ZIMTypes.ZIMFile;
import org.openzim.ZIMTypes.ZIMReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class NgramExtractorMain {
    static MurmurHash3_128 hash = new MurmurHash3_128();

    public static void main(String... args) {
    }

    private static List<String> getNgramTerms(Document document) {
        List<String> terms = new ArrayList<>();

        document.select("a[href]").forEach(e -> {
            var href = e.attr("href");
            if (href.contains(":"))
                return;
            if (href.contains("/"))
                return;

            var text = e.text().toLowerCase();
            if (!text.contains(" "))
                return;

            terms.add(text);
        });

        return terms;
    }

    public static void dumpNgramsList(
            Path zimFile,
            Path ngramFile
    ) throws IOException, InterruptedException {
        ZIMReader reader = new ZIMReader(new ZIMFile(zimFile.toString()));

        PrintWriter printWriter = new PrintWriter(Files.newOutputStream(ngramFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));

        LongOpenHashSet known = new LongOpenHashSet();

        try (var executor = Executors.newWorkStealingPool()) {
            reader.forEachArticles((title, body) -> {
                executor.submit(() -> {
                    var terms = getNgramTerms(Jsoup.parse(body));
                    synchronized (known) {
                        for (String term : terms) {
                            if (known.add(hash.hashNearlyASCII(term))) {
                                printWriter.println(term);
                            }
                        }
                    }
                });

            }, p -> true);
        }
        printWriter.close();
    }

    public static void dumpCounts(Path zimInputFile,
                                  Path countsOutputFile) throws IOException, InterruptedException
    {
        ZIMReader reader = new ZIMReader(new ZIMFile(zimInputFile.toString()));

        NgramLexicon lexicon = new NgramLexicon();

        var orderedHasher = HasherGroup.ordered();
        var unorderedHasher = HasherGroup.unordered();

        try (var executor = Executors.newWorkStealingPool()) {
            reader.forEachArticles((title, body) -> {
                executor.submit(() -> {
                    LongArrayList orderedHashes = new LongArrayList();
                    LongArrayList unorderedHashes = new LongArrayList();

                    for (var sent : getNgramTerms(Jsoup.parse(body))) {
                        String[] terms = BasicSentenceExtractor.getStemmedParts(sent);

                        orderedHashes.add(orderedHasher.rollingHash(terms));
                        unorderedHashes.add(unorderedHasher.rollingHash(terms));
                    }

                    synchronized (lexicon) {
                        for (var hash : orderedHashes) {
                            lexicon.incOrdered(hash);
                        }
                        for (var hash : unorderedHashes) {
                            lexicon.addUnordered(hash);
                        }
                    }
                });

            }, p -> true);
        }

        lexicon.saveCounts(countsOutputFile);
    }

}

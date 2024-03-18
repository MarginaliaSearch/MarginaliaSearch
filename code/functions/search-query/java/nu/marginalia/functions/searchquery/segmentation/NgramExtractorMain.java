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

    private static List<String> getNgramTerms(String title, Document document) {
        List<String> terms = new ArrayList<>();

        // Add the title
        if (title.contains(" ")) {
            terms.add(title.toLowerCase());
        }

        // Grab all internal links
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

        // Grab all italicized text
        document.getElementsByTag("i").forEach(e -> {
            var text = e.text().toLowerCase();
            if (!text.contains(" "))
                return;

            terms.add(text);
        });

        // Trim the discovered terms
        terms.replaceAll(s -> {

            // Remove trailing parentheses and their contents
            if (s.endsWith(")")) {
                int idx = s.lastIndexOf('(');
                if (idx > 0) {
                    return s.substring(0, idx).trim();
                }
            }

            // Remove leading "list of "
            if (s.startsWith("list of ")) {
                return s.substring("list of ".length());
            }

            return s;
        });

        // Remove terms that are too short or too long
        terms.removeIf(s -> {
            if (!s.contains(" "))
                return true;
            if (s.length() > 64)
                return true;
            return false;
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
                    var terms = getNgramTerms(title, Jsoup.parse(body));
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
                                  Path countsOutputFile,
                                  Path permutationsOutputFile
                                  ) throws IOException, InterruptedException
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

                    for (var sent : getNgramTerms(title, Jsoup.parse(body))) {
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
        lexicon.savePermutations(permutationsOutputFile);
    }

}

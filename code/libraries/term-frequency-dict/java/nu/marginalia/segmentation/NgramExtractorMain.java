package nu.marginalia.segmentation;

import it.unimi.dsi.fastutil.longs.*;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.util.SimpleBlockingThreadPool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openzim.ZIMTypes.ZIMFile;
import org.openzim.ZIMTypes.ZIMReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NgramExtractorMain {
    public static void main(String... args) throws IOException, InterruptedException {
        dumpCounts(Path.of("/home/vlofgren/Exports/wikipedia_en_all_nopic_2024-02.zim"),
                Path.of("/tmp/ngram-counts.bin"));
    }

    private static List<String> getNgramTitleTerms(String title) {
        List<String> terms = new ArrayList<>();

        // Add the title
        if (title.contains(" ")) { // Only add multi-word titles since we're chasing ngrams
            terms.add(title.toLowerCase());
        }

        return cleanTerms(terms);
    }

    private static List<String> getNgramBodyTerms(Document document) {
        List<String> terms = new ArrayList<>();

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

        return cleanTerms(terms);
    }

    private static List<String> cleanTerms(List<String> terms) {
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

    public static void dumpCounts(Path zimInputFile,
                                  Path countsOutputFile
                                  ) throws IOException, InterruptedException
    {
        ZIMReader reader = new ZIMReader(new ZIMFile(zimInputFile.toString()));

        NgramLexicon lexicon = new NgramLexicon();

        var orderedHasher = HasherGroup.ordered();

        var pool = new SimpleBlockingThreadPool("ngram-extractor",
                Math.clamp(2, Runtime.getRuntime().availableProcessors(), 32),
                32
                );

        reader.forEachArticles((title, body) -> {
            pool.submitQuietly(() -> {
                LongArrayList orderedHashesTitle = new LongArrayList();
                LongArrayList orderedHashesBody = new LongArrayList();

                String normalizedTitle = title.replace('_', ' ');

                for (var sent : getNgramTitleTerms(normalizedTitle)) {
                    String[] terms = BasicSentenceExtractor.getStemmedParts(sent);
                    orderedHashesTitle.add(orderedHasher.rollingHash(terms));
                }

                for (var sent : getNgramBodyTerms(Jsoup.parse(body))) {
                    String[] terms = BasicSentenceExtractor.getStemmedParts(sent);
                    orderedHashesBody.add(orderedHasher.rollingHash(terms));
                }

                synchronized (lexicon) {
                    for (var hash : orderedHashesTitle) {
                        lexicon.incOrderedTitle(hash);
                    }
                    for (var hash : orderedHashesBody) {
                        lexicon.incOrderedBody(hash);
                    }
                }
            });

        }, p -> true);

        pool.shutDown();
        pool.awaitTermination(10, TimeUnit.DAYS);

        lexicon.saveCounts(countsOutputFile);
    }

}

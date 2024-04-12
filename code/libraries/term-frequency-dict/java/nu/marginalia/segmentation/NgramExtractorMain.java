package nu.marginalia.segmentation;

import it.unimi.dsi.fastutil.longs.*;
import nu.marginalia.hash.MurmurHash3_128;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openzim.ZIMTypes.ZIMFile;
import org.openzim.ZIMTypes.ZIMReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class NgramExtractorMain {
    static MurmurHash3_128 hash = new MurmurHash3_128();

    public static void main(String... args) {
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

        try (var executor = Executors.newWorkStealingPool()) {
            reader.forEachArticles((title, body) -> {
                executor.submit(() -> {
                    LongArrayList orderedHashesTitle = new LongArrayList();
                    LongArrayList orderedHashesBody = new LongArrayList();

                    for (var sent : getNgramTitleTerms(title)) {
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
        }

        lexicon.saveCounts(countsOutputFile);
    }

}

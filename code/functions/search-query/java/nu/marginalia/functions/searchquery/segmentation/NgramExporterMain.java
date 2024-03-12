package nu.marginalia.functions.searchquery.segmentation;

import nu.marginalia.WmsaHome;
import nu.marginalia.language.sentence.SentenceExtractor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;

public class NgramExporterMain {

    public static void main(String... args) throws IOException {
        trial();
    }

    static void trial() throws IOException {
        SentenceExtractor se = new SentenceExtractor(WmsaHome.getLanguageModels());

        NgramLexicon lexicon = new NgramLexicon();
        lexicon.loadCounts(Path.of("/home/vlofgren/ngram-counts.bin"));

        System.out.println("Loaded!");

        var scanner = new Scanner(System.in);
        for (;;) {
            System.out.println("Enter a sentence: ");
            String line = scanner.nextLine();
            System.out.println(".");
            if (line == null)
                break;

            String[] terms = BasicSentenceExtractor.getStemmedParts(line);
            System.out.println(".");

            for (int i = 2; i< 8; i++) {
                lexicon.findSegments(i, terms).forEach(p -> {
                    System.out.println(STR."\{Arrays.toString(p.project(terms))}: \{p.count()}");
                });
            }

        }
    }


}

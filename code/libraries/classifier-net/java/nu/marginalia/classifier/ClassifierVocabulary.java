package nu.marginalia.classifier;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassifierVocabulary {
    private List<String> vocabulary;
    private Map<String, Integer> vocabularyInv;

    public int size() {
        return vocabulary.size();
    }

    public ClassifierVocabulary(List<String> terms) {
        vocabulary = new ArrayList<>(terms);
        vocabularyInv = new HashMap<>();

        for (int i = 0; i < terms.size(); i++) {
            vocabularyInv.put(terms.get(i), i);
        }
    }


    public ClassifierVocabulary(Path file) throws IOException {
        List<String> goodLines = new ArrayList<>();

        for (String line: Files.readAllLines(file)) {
            if (line.isBlank())
                continue;
            line = line.trim();
            if (line.startsWith("#"))
                continue;
            goodLines.add(line);
        }

        this(goodLines);
    }

    public void save(Path outputFile) throws IOException {
        try (var pw = new PrintWriter(Files.newBufferedWriter(outputFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (String feature : vocabulary) {
                pw.println(feature);
            }
        }
    }

    public int[] features(String sent) {
        IntSet features = new IntArraySet();

        for (String term : StringUtils.split(sent.toLowerCase())) {
            Integer idx = vocabularyInv.get(term);
            if (idx != null) {
                features.add(idx.intValue());
            }
        }

        return features.toIntArray();
    }

    public ClassifierSample createSample(String sent, boolean label) {
        int[] features = features(sent);
        return new ClassifierSample(features, label ? 1 : 0);
    }

}

package nu.marginalia.classifier;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import nu.marginalia.language.model.DocumentSentence;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class ClassifierVocabulary {
    private List<String> vocabulary;
    private Map<String, Integer> vocabularyInv;
    private Map<String, Map<String, Integer>> bigramIdx = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(ClassifierVocabulary.class);
    public int size() {
        return vocabulary.size();
    }

    public ClassifierVocabulary(List<String> terms) {
        vocabulary = new ArrayList<>(terms);
        vocabularyInv = new HashMap<>();

        int unigrams = 0;
        int bigrams = 0;

        for (int i = 0; i < terms.size(); i++) {
            String term = terms.get(i);
            if (!term.contains("_")) {
                vocabularyInv.put(terms.get(i), i);
                unigrams++;
            }
            else {
                String[] parts = StringUtils.split(term, "_", 2);
                bigramIdx.computeIfAbsent(parts[0],
                        _ -> new HashMap<>())
                        .put(parts[1], i);
                bigrams++;
            }
        }

        logger.info("Loaded {} unigrams and {} bigrams", unigrams, bigrams);

    }


    public List<String> featuresReverse(int[] x) {
        return Arrays.stream(x).mapToObj(i -> vocabulary.get(i)).toList();
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
        try (var pw = new PrintWriter(Files.newBufferedWriter(outputFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)))
        {
            for (String feature : vocabulary) {
                pw.println(feature);
            }
        }
    }

    public int[] features(String... sentences) {
        IntSet features = new IntArraySet();

        for (String sent: sentences) {
            String prevTerm = null;

            for (String term : StringUtils.split(sent.toLowerCase())) {
                term = trimTerm(term);


                Integer idx = vocabularyInv.get(term);
                if (idx != null) {
                    features.add(idx.intValue());
                }

                idx = bigramIdx(prevTerm, term);
                if (idx >= 0) {
                    features.add(idx);
                }

                prevTerm = term;
            }
        }

        return features.toIntArray();
    }


    public Map.Entry<int[], int[]> countedFeatures(String... sentences) {
        Int2IntMap features = new Int2IntArrayMap();

        for (String sent: sentences) {
            String prevTerm = null;

            for (String term : StringUtils.split(sent.toLowerCase())) {
                term = trimTerm(term);

                Integer idx = vocabularyInv.get(term);
                if (idx != null) {
                    features.mergeInt(idx.intValue(), 1, Integer::sum);
                }

                idx = bigramIdx(prevTerm, term);
                if (idx >= 0) {
                    features.mergeInt(idx.intValue(), 1, Integer::sum);
                }

                prevTerm = term;
            }
        }

        return Map.entry(features.keySet().toIntArray(), features.values().toIntArray());
    }

    public int[] features(List<DocumentSentence> sentences) {
        IntSet features = new IntArraySet();

        for (DocumentSentence sent: sentences) {
            String prevTerm = null;

            for (int i = 0; i < sent.length(); i++) {

                if (sent.isStopWord(i)) {
                    prevTerm = null;
                    continue;
                }

                String term = sent.wordsLowerCase[i];
                term = trimTerm(term);

                Integer idx = vocabularyInv.get(term);
                if (idx != null) {
                    features.add(idx.intValue());
                }

                int bigramIdx = bigramIdx(prevTerm, term);
                if (bigramIdx >= 0) {
                    features.add(bigramIdx);
                }

                if (sent.isSeparatorSpace(i)) {
                    prevTerm = term;
                }
                else {
                    prevTerm = null;
                }
            }
        }

        return features.toIntArray();
    }


    public Map.Entry<int[], int[]> countedFeatures(List<DocumentSentence> sentences) {
        Int2IntMap features = new Int2IntArrayMap();

        for (DocumentSentence sent: sentences) {
            String prevTerm = null;

            for (int i = 0; i < sent.length(); i++) {

                if (sent.isStopWord(i)) {
                    prevTerm = null;
                    continue;
                }

                String term = sent.wordsLowerCase[i];
                term = trimTerm(term);

                Integer idx = vocabularyInv.get(term);
                if (idx != null) {
                    features.mergeInt(idx.intValue(), 1, Integer::sum);
                }

                int bigramIdx = bigramIdx(prevTerm, term);
                if (bigramIdx >= 0) {
                    features.mergeInt(bigramIdx, 1, Integer::sum);
                }

                if (sent.isSeparatorSpace(i)) {
                    prevTerm = term;
                }
                else {
                    prevTerm = null;
                }
            }
        }

        return Map.entry(features.keySet().toIntArray(), features.values().toIntArray());
    }

    private int bigramIdx(String a, String b) {
        if (a == null)
            return -1;

        Map<String, Integer> suffixes = bigramIdx.get(a);
        if (null == suffixes)
            return -1;

        Integer idx = suffixes.get(b);
        if (idx == null) return -1;
        return idx;
    }

    private String trimTerm(String term) {
        int start = 0;
        int end = term.length();
        while (start < end) {
            int c = term.charAt(start);
            if (Character.isAlphabetic(c) || Character.isDigit(c))
                break;
            start++;
        }

        while (end >= start && end > 0) {
            int c = term.charAt(end-1);
            if (Character.isAlphabetic(c) || Character.isDigit(c))
                break;
            end--;
        }

        if (end > start) {
            return term.substring(start, end);
        }
        else {
            return "";
        }
    }


    public ClassifierSample createSample(BinaryClassifierModel.InputActivationMode activationMode, String sent, boolean label) {
        if (activationMode == BinaryClassifierModel.InputActivationMode.BINARY) {
            int[] features = features(sent);

            return ClassifierSample.ofBinary(features, label ? 1 : 0);
        }
        else if (activationMode == BinaryClassifierModel.InputActivationMode.COUNTED) {
            Map.Entry<int[], int[]> features = countedFeatures(sent);

            return ClassifierSample.ofCounted(features.getKey(), features.getValue(), label ? 1 : 0);
        }
        else {
            throw new IllegalArgumentException("Enum has grown, I don't know what to do with " + activationMode);
        }
    }


}

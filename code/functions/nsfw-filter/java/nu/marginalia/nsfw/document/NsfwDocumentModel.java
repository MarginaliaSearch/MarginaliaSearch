package nu.marginalia.nsfw.document;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.array.FloatArrayColumn;
import nu.marginalia.slop.column.primitive.FloatColumn;
import nu.marginalia.slop.desc.StorageType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

class NsfwDocumentModel {

    private static final Logger logger = LoggerFactory.getLogger(NsfwDocumentModel.class);

    static final String FEATURES_FILENAME = "features.txt";

    static final String LABEL_NSFW = "__label__NSFW";
    static final String LABEL_SAFE = "__label__SAFE";

    private static final int HIDDEN_SIZE = 16;

    // Slop column definitions for weight serialization
    private static final FloatArrayColumn weightsIHColumn =
            new FloatArrayColumn("weightsIH", StorageType.PLAIN);
    private static final FloatColumn biasHColumn =
            new FloatColumn("biasH");
    private static final FloatColumn weightsHOColumn =
            new FloatColumn("weightsHO");
    private static final FloatColumn biasOColumn =
            new FloatColumn("biasO");

    // Feature vocabulary
    private final List<String> terms;
    private final int numFeatures;
    private final Map<String, Integer> unigramIndex;
    private final Map<String, Integer> bigramIndex;

    // Network weights: input -> hidden
    private final float[][] weightsIH;
    private final float[] biasH;

    // Network weights: hidden -> output
    private final float[] weightsHO;
    private float biasO;

    static NsfwDocumentModel createForTraining(Path featuresFile) throws IOException {
        return new NsfwDocumentModel(loadTermsFromFile(featuresFile));
    }

    NsfwDocumentModel(Path directory) throws IOException {
        this(loadTermsFromFile(directory.resolve(FEATURES_FILENAME)));
        loadWeights(directory);
    }

    NsfwDocumentModel(List<String> terms) {
        this.terms = terms;
        this.numFeatures = terms.size();
        this.unigramIndex = new HashMap<>();
        this.bigramIndex = new HashMap<>();

        for (int i = 0; i < terms.size(); i++) {
            String term = terms.get(i);
            if (term.indexOf('_') >= 0) {
                bigramIndex.put(term, i);
            } else {
                unigramIndex.put(term, i);
            }
        }

        this.weightsIH = new float[numFeatures][HIDDEN_SIZE];
        this.biasH = new float[HIDDEN_SIZE];
        this.weightsHO = new float[HIDDEN_SIZE];
        this.biasO = 0.0f;

        Random random = new Random(42);
        float scaleIH = (float) Math.sqrt(2.0 / (numFeatures + HIDDEN_SIZE));
        for (int i = 0; i < numFeatures; i++) {
            for (int h = 0; h < HIDDEN_SIZE; h++) {
                weightsIH[i][h] = (float) random.nextGaussian() * scaleIH;
            }
        }
        float scaleHO = (float) Math.sqrt(2.0 / (HIDDEN_SIZE + 1));
        for (int h = 0; h < HIDDEN_SIZE; h++) {
            weightsHO[h] = (float) random.nextGaussian() * scaleHO;
        }
    }

    int[] extractFeatures(String title, String description) {
        String text = sanitizeInput(title + " " + description);
        String[] words = StringUtils.split(text);

        IntArrayList active = new IntArrayList();

        String prevWord = null;
        for (String word : words) {
            Integer idx = unigramIndex.get(word);
            if (idx != null) {
                active.add(idx.intValue());
            }

            if (prevWord != null && !bigramIndex.isEmpty()) {
                Integer bigramIdx = bigramIndex.get(prevWord + "_" + word);
                if (bigramIdx != null) {
                    active.add(bigramIdx.intValue());
                }
            }
            prevWord = word;
        }

        return active.toIntArray();
    }

    Set<String> knownTerms() {
        Set<String> all = new HashSet<>(unigramIndex.keySet());
        all.addAll(bigramIndex.keySet());
        return all;
    }

    int numFeatures() {
        return numFeatures;
    }

    Map<String, Float> featureImportance() {
        Map<String, Float> importance = new HashMap<>();

        for (int i = 0; i < numFeatures; i++) {
            float score = 0.0f;
            for (int h = 0; h < HIDDEN_SIZE; h++) {
                score += Math.abs(weightsIH[i][h] * weightsHO[h]);
            }
            importance.put(terms.get(i), score);
        }

        return importance;
    }

    float forward(int[] activeFeatures) {
        float[] hidden = new float[HIDDEN_SIZE];

        for (int h = 0; h < HIDDEN_SIZE; h++) {
            float sum = biasH[h];
            for (int i : activeFeatures) {
                sum += weightsIH[i][h];
            }
            hidden[h] = sigmoid(sum);
        }

        float output = biasO;
        for (int h = 0; h < HIDDEN_SIZE; h++) {
            output += hidden[h] * weightsHO[h];
        }

        return sigmoid(output);
    }

    float trainSample(int[] activeFeatures, float label, float learningRate) {
        float[] hiddenRaw = new float[HIDDEN_SIZE];
        float[] hidden = new float[HIDDEN_SIZE];

        for (int h = 0; h < HIDDEN_SIZE; h++) {
            hiddenRaw[h] = biasH[h];
            for (int i : activeFeatures) {
                hiddenRaw[h] += weightsIH[i][h];
            }
            hidden[h] = sigmoid(hiddenRaw[h]);
        }

        float outputRaw = biasO;
        for (int h = 0; h < HIDDEN_SIZE; h++) {
            outputRaw += hidden[h] * weightsHO[h];
        }
        float output = sigmoid(outputRaw);
        final float eps = 1e-7f;

        float loss = (float) (-label * Math.log(output + eps)
                - (1.0f - label) * Math.log(1.0f - output + eps));

        float dOutput = output - label;

        for (int h = 0; h < HIDDEN_SIZE; h++) {
            weightsHO[h] -= learningRate * dOutput * hidden[h];
        }
        biasO -= learningRate * dOutput;

        for (int h = 0; h < HIDDEN_SIZE; h++) {
            float dHidden = dOutput * weightsHO[h] * sigmoidDerivative(hiddenRaw[h]);

            for (int i : activeFeatures) {
                weightsIH[i][h] -= learningRate * dHidden;
            }
            biasH[h] -= learningRate * dHidden;
        }

        return loss;
    }

    void saveWeights(Path directory) throws IOException {
        Files.createDirectories(directory);

        try (SlopTable table = new SlopTable(directory)) {
            FloatArrayColumn.Writer weightsIHWriter = weightsIHColumn.create(table);
            FloatColumn.Writer biasHWriter = biasHColumn.create(table);
            FloatColumn.Writer weightsHOWriter = weightsHOColumn.create(table);
            FloatColumn.Writer biasOWriter = biasOColumn.create(table);

            for (int h = 0; h < HIDDEN_SIZE; h++) {
                float[] col = new float[numFeatures];
                for (int i = 0; i < numFeatures; i++) {
                    col[i] = weightsIH[i][h];
                }
                weightsIHWriter.put(col);
                biasHWriter.put(biasH[h]);
                weightsHOWriter.put(weightsHO[h]);
                biasOWriter.put(biasO);
            }
        }

        Files.write(directory.resolve(FEATURES_FILENAME), terms);
    }

    private void loadWeights(Path directory) throws IOException {
        try (SlopTable table = new SlopTable(directory)) {
            FloatArrayColumn.Reader weightsIHReader = weightsIHColumn.open(table);
            FloatColumn.Reader biasHReader = biasHColumn.open(table);
            FloatColumn.Reader weightsHOReader = weightsHOColumn.open(table);
            FloatColumn.Reader biasOReader = biasOColumn.open(table);

            for (int h = 0; h < HIDDEN_SIZE; h++) {
                float[] col = weightsIHReader.get();
                if (col.length != numFeatures) {
                    throw new IOException("Weight dimensions mismatch: expected "
                            + numFeatures + " but got " + col.length);
                }
                for (int i = 0; i < numFeatures; i++) {
                    weightsIH[i][h] = col[i];
                }
                biasH[h] = biasHReader.get();
                weightsHO[h] = weightsHOReader.get();
                biasO = biasOReader.get();
            }
        }
    }

    static List<String> loadTermsFromFile(Path path) throws IOException {
        List<String> terms = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String line : Files.readAllLines(path)) {
            line = line.trim();

            if (line.isEmpty())
                continue;
            if (line.startsWith("#"))
                continue;

            if (!seen.add(line)) {
                logger.warn("Duplicate feature term: '{}'", line);
                continue;
            }
            terms.add(line);
        }

        return terms;
    }

    private static String sanitizeInput(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c + 32));
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' ') {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private static float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    private static float sigmoidDerivative(float x) {
        float s = sigmoid(x);
        return s * (1.0f - s);
    }
}

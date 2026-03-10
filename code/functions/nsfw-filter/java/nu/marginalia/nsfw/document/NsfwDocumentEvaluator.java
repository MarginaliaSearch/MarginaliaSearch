package nu.marginalia.nsfw.document;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import nu.marginalia.WmsaHome;
import nu.marginalia.model.EdgeUrl;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/** Evaluates a trained NSFW model against test data or real document
 *  database data.  Supports two modes:
 *  <ul>
 *      <li>Labeled test set evaluation with precision/recall/F1 metrics</li>
 *      <li>Active learning: scan a document database, send positive cases
 *          to Ollama for LLM-based labeling, and write the results
 *          as new training data
*       </li>
 *  </ul>
 */
public class NsfwDocumentEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(NsfwDocumentEvaluator.class);

    // Ambiguity band: documents with probability in this range
    // are sent to the LLM for labeling
    private static final float AMBIGUOUS_LOW = 0.8f;
    private static final float AMBIGUOUS_HIGH = 1.0f;
    private static final int MAX_LABELS = 1000;

    private static final Path DATA_DIR =
            Path.of("code/functions/nsfw-filter/data");
    private static final Path TRAINING_DATA_DIR = DATA_DIR.resolve("training-data");
    private static final Path FEATURES_FILE = DATA_DIR.resolve("features.txt");

    public static void main(String[] args) throws IOException, SQLException {
        String cmd;
        if (args.length == 0) {
            cmd = "";
        }
        else {
            cmd = args[0];
        }

        switch (cmd) {
            case "evaluate" -> {
                runEvaluation();
            }
            case "active-learning-docdb" -> {
                if (args.length < 2) {
                    System.err.println("Mode takes a document db as argument");
                    System.err.println("usage: active-learning db-path");
                    return;
                }

                runActiveLearningDocDb(
                        Path.of(args[1]),
                        WmsaHome.getModelsPath().resolve("nsfw-model")
                );
            }
            case "active-learning-api" -> {
                if (args.length < 2) {
                    System.err.println("Mode takes an API key  as argument");
                    System.err.println("usage: active-learning api-key");
                    return;
                }

                runActiveLearningSearchAPI(
                        args[1],
                        WmsaHome.getModelsPath().resolve("nsfw-model"),
                        Arrays.stream(args).skip(2).collect(Collectors.joining(" "))
                );
            }
            default -> {
                System.err.println("Usage: [evaluate|active-learning]");
            }
        }
    }

    /** Train on labeled data and evaluate against a held-out test set.
     *  All .txt files in the training-data directory are
     *  randomly split 80/20 into training and testing.
     */
    static void runEvaluation() throws IOException {

        List<Path> dataFiles = NsfwDocumentTrainer.listTrainingFiles(TRAINING_DATA_DIR);

        logger.info("Found {} training data files in {}", dataFiles.size(), TRAINING_DATA_DIR);

        Path[] split = NsfwDocumentTrainer.splitTrainingData(0.8,
                dataFiles.toArray(new Path[0]));
        Path trainingPath = split[0];
        Path testingPath = split[1];

        logger.info("Training split: {}, testing split: {}", trainingPath, testingPath);

        NsfwDocumentTrainer trainer = new NsfwDocumentTrainer(FEATURES_FILE);
        trainer.train(0.1f, 1000, trainingPath);
        trainer.saveWeights(Path.of("/home/vlofgren/Code/MarginaliaSearch/run/model/nsfw-model"));

        evaluate(trainer.getModel(), testingPath);
    }

    /** Scan real documents, find ambiguous cases, and label them
     *  via Ollama.  Results are written as a timestamped file in
     *  the training data directory.
     */
    static void runActiveLearningDocDb(Path docDbPath,
                                   Path modelPath) throws IOException, SQLException {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        Path outputPath = TRAINING_DATA_DIR.resolve("ollama-" + timestamp + ".txt");

        NsfwDocumentFilter filter = new NsfwDocumentFilter(modelPath);

        IntOpenHashSet seenHashes = loadExistingHashes(TRAINING_DATA_DIR);

        int labeled = 0;
        int skipped = 0;
        int ollamaNsfw = 0;
        int ollamaSafe = 0;
        int modelAgreed = 0;

        try (Connection docDbConn = DriverManager.getConnection("jdbc:sqlite:" + docDbPath);
             Statement stmt = docDbConn.createStatement();
             OllamaNsfwLabeler ollama = new OllamaNsfwLabeler("localhost", 11434, OllamaNsfwLabeler.DEFAULT_MODEL);
             BufferedWriter writer = Files.newBufferedWriter(outputPath,
                     StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            stmt.setFetchSize(1000);

            ResultSet rs = stmt.executeQuery("SELECT TITLE, DESCRIPTION, URL FROM DOCUMENT");

            String lastLabeledDomain = null;

            while (rs.next() && labeled < MAX_LABELS) {
                String title = rs.getString("TITLE");
                String description = rs.getString("DESCRIPTION");
                String url = rs.getString("URL");

                if (title == null || title.isBlank()) {
                    continue;
                }

                // After labeling a sample, skip remaining rows from the
                // same domain to avoid bias from domain-sorted URLs
                if (lastLabeledDomain != null) {
                    String domain = extractDomain(url);
                    if (lastLabeledDomain.equals(domain)) {
                        continue;
                    }
                    lastLabeledDomain = null;
                }

                float proba = filter.nsfwProba(title, description);

                // Only send ambiguous cases to the LLM
                if (proba < AMBIGUOUS_LOW || proba > AMBIGUOUS_HIGH) {
                    continue;
                }

                String text = (title + " " + (description != null ? description : ""))
                        .toLowerCase()
                        .replaceAll("[\\r\\n]+", " ");

                if (!seenHashes.add(text.hashCode())) {
                    continue;
                }

                String label;
                try {
                    label = ollama.classify(title, description != null ? description : "");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    logger.warn("Ollama request failed for '{}': {}", title, e.getMessage());
                    skipped++;
                    continue;
                }

                boolean ollamaSaysNsfw = NsfwDocumentModel.LABEL_NSFW.equals(label);
                boolean modelSaysNsfw = proba >= 0.5f;

                if (ollamaSaysNsfw) {
                    ollamaNsfw++;
                } else {
                    ollamaSafe++;
                }
                if (ollamaSaysNsfw == modelSaysNsfw) {
                    modelAgreed++;
                }

                writer.write(label + " " + text);
                writer.newLine();
                writer.flush();

                labeled++;
                lastLabeledDomain = extractDomain(url);

                logger.info("[{}] p={} {} | {} | {}",
                        label, String.format("%.2f", proba), url, title, description);

                if (labeled % 100 == 0) {
                    logger.info("Progress: {} labeled, {} skipped", labeled, skipped);
                }
            }
        }

        logger.info("Active learning complete: {} labeled, {} skipped, written to {}",
                labeled, skipped, outputPath);
        logger.info("Ollama labels: {} NSFW, {} SAFE", ollamaNsfw, ollamaSafe);
        if (labeled > 0) {
            logger.info("Model agreed with Ollama on {}/{} ({} %)",
                    modelAgreed, labeled, 100 * modelAgreed / labeled);
        }
    }



    /** Scan real documents, find ambiguous cases, and label them
     *  via Ollama.  Results are written as a timestamped file in
     *  the training data directory.
     */
    static void runActiveLearningSearchAPI(String apiKey, Path modelPath, String query) throws IOException, SQLException {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        Path outputPath = TRAINING_DATA_DIR.resolve("ollama-" + timestamp + ".txt");

        NsfwDocumentFilter filter = new NsfwDocumentFilter(modelPath);

        IntOpenHashSet seenHashes = loadExistingHashes(TRAINING_DATA_DIR);

        int labeled = 0;
        int skipped = 0;
        int ollamaNsfw = 0;
        int ollamaSafe = 0;
        int modelAgreed = 0;

        try (MarginaliaApiClient apiClient = new MarginaliaApiClient(apiKey);
             OllamaNsfwLabeler ollama = new OllamaNsfwLabeler("localhost", 11434, OllamaNsfwLabeler.DEFAULT_MODEL);
             BufferedWriter writer = Files.newBufferedWriter(outputPath,
                     StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            String lastLabeledDomain = null;


            int page = 1;

            while (true) {
                var rsp = apiClient.query(query + " qs=RF_TITLE", page++, 100, 2);
                if (rsp.results().isEmpty())
                    break;

                for (var result : rsp.results()) {
                    String title = result.title();
                    String description = result.description();
                    String url = result.url();

                    if (title == null || title.isBlank()) {
                        continue;
                    }

                    // After labeling a sample, skip remaining rows from the
                    // same domain to avoid bias from domain-sorted URLs
                    if (lastLabeledDomain != null) {
                        String domain = extractDomain(url);
                        if (lastLabeledDomain.equals(domain)) {
                            continue;
                        }
                        lastLabeledDomain = null;
                    }

                    float proba = filter.nsfwProba(title, description);

                    String text = (title + " " + (description != null ? description : ""))
                            .toLowerCase()
                            .replaceAll("[\\r\\n]+", " ");

                    if (!seenHashes.add(text.hashCode())) {
                        continue;
                    }

                    String label;
                    try {
                        label = ollama.classify(title, description != null ? description : "");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        logger.warn("Ollama request failed for '{}': {}", title, e.getMessage());
                        skipped++;
                        continue;
                    }

                    boolean ollamaSaysNsfw = NsfwDocumentModel.LABEL_NSFW.equals(label);
                    boolean modelSaysNsfw = proba >= 0.5f;

                    if (ollamaSaysNsfw) {
                        ollamaNsfw++;
                    } else {
                        ollamaSafe++;
                    }
                    if (ollamaSaysNsfw == modelSaysNsfw) {
                        modelAgreed++;
                    }

                    writer.write(label + " " + text);
                    writer.newLine();
                    writer.flush();

                    labeled++;
                    lastLabeledDomain = extractDomain(url);

                    logger.info("[{}] p={} {} | {} | {}",
                            label, String.format("%.2f", proba), url, title, description);

                    if (labeled % 100 == 0) {
                        logger.info("Progress: {} labeled, {} skipped", labeled, skipped);
                    }
                }
            }
        }

        logger.info("Active learning complete: {} labeled, {} skipped, written to {}",
                labeled, skipped, outputPath);
        logger.info("Ollama labels: {} NSFW, {} SAFE", ollamaNsfw, ollamaSafe);
        if (labeled > 0) {
            logger.info("Model agreed with Ollama on {}/{} ({} %)",
                    modelAgreed, labeled, 100 * modelAgreed / labeled);
        }
    }

    /** Load hash codes of already-labeled text lines from all
     *  training data files in the directory, so that subsequent
     *  runs skip previously seen entries.
     */
    private static IntOpenHashSet loadExistingHashes(Path directory) throws IOException {
        IntOpenHashSet hashes = new IntOpenHashSet();

        for (Path file : NsfwDocumentTrainer.listTrainingFiles(directory)) {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int spaceIdx = line.indexOf(' ');
                    if (spaceIdx >= 0) {
                        hashes.add(line.substring(spaceIdx + 1).hashCode());
                    }
                }
            }
        }

        logger.info("Loaded {} existing entry hashes from {}", hashes.size(), directory);
        return hashes;
    }

    /** Extract the domain from a URL string, or return the URL
     *  itself if parsing fails.
     */
    private static String extractDomain(String url) {
        return EdgeUrl.parse(url)
                .map(u -> u.domain.toString())
                .orElse(url);
    }

    /** Evaluate model accuracy against a labeled test set. */
    static void evaluate(NsfwDocumentModel model,
                          Path testingPath) throws IOException {
        int total = 0;
        int correct = 0;
        int truePositive = 0;
        int falsePositive = 0;
        int trueNegative = 0;
        int falseNegative = 0;

        Path fnPath = Path.of("/tmp/nsfw_false_negatives.txt");
        Path fpPath = Path.of("/tmp/nsfw_false_positives.txt");

        try (BufferedReader reader = Files.newBufferedReader(testingPath);
             BufferedWriter fnWriter = Files.newBufferedWriter(fnPath);
             BufferedWriter fpWriter = Files.newBufferedWriter(fpPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                int spaceIdx = line.indexOf(' ');
                if (spaceIdx < 0) {
                    continue;
                }

                String labelStr = line.substring(0, spaceIdx);
                boolean expected;
                if (NsfwDocumentModel.LABEL_NSFW.equals(labelStr)) {
                    expected = true;
                } else if (NsfwDocumentModel.LABEL_SAFE.equals(labelStr)) {
                    expected = false;
                } else {
                    continue;
                }

                String text = line.substring(spaceIdx + 1);
                float proba = model.forward(model.extractFeatures(text, ""));
                boolean predicted = proba >= 0.5f;

                total++;
                if (predicted == expected) {
                    correct++;
                }
                if (expected && predicted) {
                    truePositive++;
                } else if (!expected && predicted) {
                    falsePositive++;
                    fpWriter.write(String.format("%.4f %s%n", proba, text));
                } else if (!expected) {
                    trueNegative++;
                } else {
                    falseNegative++;
                    fnWriter.write(String.format("%.4f %s%n", proba, text));
                }
            }
        }

        System.out.println("=== Test Results ===");
        System.out.println("Total:      " + total);
        System.out.println("Correct:    " + correct);
        System.out.printf("Accuracy:   %.2f%%%n", 100.0 * correct / total);
        System.out.println();
        System.out.println("True  Positive: " + truePositive);
        System.out.println("False Positive: " + falsePositive);
        System.out.println("True  Negative: " + trueNegative);
        System.out.println("False Negative: " + falseNegative);

        double precision = truePositive + falsePositive > 0
                ? (double) truePositive / (truePositive + falsePositive) : 0;
        double recall = truePositive + falseNegative > 0
                ? (double) truePositive / (truePositive + falseNegative) : 0;
        double f1 = precision + recall > 0
                ? 2 * precision * recall / (precision + recall) : 0;

        System.out.printf("Precision:  %.4f%n", precision);
        System.out.printf("Recall:     %.4f%n", recall);
        System.out.printf("F1:         %.4f%n", f1);
        System.out.println();
        System.out.println("False negatives written to " + fnPath);
        System.out.println("False positives written to " + fpPath);

        suggestFeatures(fnPath, fpPath, model);
        suggestRemovals(model);
    }

    static void suggestFeatures(Path fnPath, Path fpPath,
                                NsfwDocumentModel model) throws IOException {
        Map<String, Integer> fnWordCounts = countWords(fnPath);
        Map<String, Integer> fpWordCounts = countWords(fpPath);

        if (fnWordCounts.isEmpty() || fpWordCounts.isEmpty()) {
            return;
        }

        // Remove low-frequency noise
        fnWordCounts.values().removeIf(count -> count < 3);
        fpWordCounts.values().removeIf(count -> count < 3);

        Set<String> knownTerms = model.knownTerms();

        System.out.println();
        System.out.println("=== Candidate NSFW-indicative features (over-represented in false negatives) ===");
        printCandidates(fnWordCounts, fpWordCounts, knownTerms);

        System.out.println();
        System.out.println("=== Candidate SAFE-indicative features (over-represented in false positives) ===");
        printCandidates(fpWordCounts, fnWordCounts, knownTerms);
    }

    static void suggestRemovals(NsfwDocumentModel model) {
        Map<String, Float> importance = model.featureImportance();

        List<Map.Entry<String, Float>> sorted = new ArrayList<>(importance.entrySet());
        sorted.sort(Map.Entry.comparingByValue());

        System.out.println();
        System.out.println("=== Low-importance features (candidates for removal) ===");

        int limit = Math.min(sorted.size(), 50);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Float> entry = sorted.get(i);
            System.out.printf("  %-30s  importance=%.6f%n",
                    entry.getKey(), entry.getValue());
        }
    }

    private static Map<String, Integer> countWords(Path path) throws IOException {
        Map<String, Integer> wordCounts = new HashMap<>();

        if (!Files.exists(path)) {
            return wordCounts;
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip the probability prefix
                int spaceIdx = line.indexOf(' ');
                if (spaceIdx < 0) {
                    continue;
                }
                String text = line.substring(spaceIdx + 1)
                        .replaceAll("[^a-z0-9 ]", " ");

                String[] words = StringUtils.split(text);
                String prevWord = null;

                for (String word : words) {
                    if (word.length() > 2) {
                        wordCounts.merge(word, 1, Integer::sum);
                    }

                    if (prevWord != null && prevWord.length() > 1 && word.length() > 1) {
                        wordCounts.merge(prevWord + "_" + word, 1, Integer::sum);
                    }
                    prevWord = word;
                }
            }
        }

        return wordCounts;
    }

    private static void printCandidates(Map<String, Integer> source,
                                        Map<String, Integer> other,
                                        Set<String> knownTerms) {
        double factor = other.size() / (double) source.size();

        List<Map.Entry<String, Double>> candidates = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String term = entry.getKey();
            if (knownTerms.contains(term)) {
                continue;
            }
            double ratio = entry.getValue() * factor / other.getOrDefault(term, 1);
            if (ratio > 5) {
                candidates.add(Map.entry(term, ratio));
            }
        }

        candidates.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        int limit = Math.min(candidates.size(), 50);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Double> entry = candidates.get(i);
            System.out.printf("  %-30s  ratio=%.1f  count=%d%n",
                    entry.getKey(), entry.getValue(),
                    source.get(entry.getKey()));
        }
    }
}

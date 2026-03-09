package nu.marginalia.nsfw.document;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Trains the NSFW neural network from a labeled data file and
 *  saves the resulting weights.
 *  <p>
 *  Training data is expected with one sample per line:
 *  <pre>__label__NSFW lowercased text...</pre>
 *  or
 *  <pre>__label__SAFE lowercased text...</pre>
 */
public class NsfwDocumentTrainer {

    private static final Logger logger = LoggerFactory.getLogger(NsfwDocumentTrainer.class);

    private final NsfwDocumentModel model;

    /** Create a trainer loading features from the given file.
     *
     * @param featuresFile path to the features.txt vocabulary file
     */
    public NsfwDocumentTrainer(Path featuresFile) throws IOException {
        this.model = NsfwDocumentModel.createForTraining(featuresFile);
    }

    /** Train a model from the data directory and save it to the
     *  output directory.  Expects the data directory to contain
     *  features.txt and a training-data/ subdirectory with .txt files.
     *
     * @param args [0] = data directory, [1] = output model directory
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: NsfwDocumentTrainer <data-dir> <output-dir>");
            System.exit(1);
        }

        Path dataDir = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);

        Path featuresFile = dataDir.resolve("features.txt");
        Path trainingDataDir = dataDir.resolve("training-data");

        List<Path> dataFiles = listTrainingFiles(trainingDataDir);
        logger.info("Found {} training data files in {}", dataFiles.size(), trainingDataDir);

        Path[] split = splitTrainingData(0.8, dataFiles.toArray(new Path[0]));

        NsfwDocumentTrainer trainer = new NsfwDocumentTrainer(featuresFile);
        trainer.train(0.1f, 1000, split[0]);
        trainer.saveWeights(outputDir);

        logger.info("Model saved to {}", outputDir);

        NsfwDocumentEvaluator.evaluate(trainer.getModel(), split[1]);
    }

    /** Train the model from one or more labeled data files.
     *
     * @param learningRate learning rate for gradient descent
     * @param epochs number of training epochs
     * @param trainingDataPaths paths to training data files
     */
    public void train(float learningRate, int epochs, Path... trainingDataPaths) throws IOException {
        List<int[]> featuresList = new ArrayList<>();
        FloatArrayList labelsList = new FloatArrayList();

        for (Path path : trainingDataPaths) {
            readTrainingData(path, featuresList, labelsList);
        }

        if (featuresList.isEmpty()) {
            logger.warn("No training data found");
            return;
        }

        logger.info("Training NSFW model with {} samples, {} epochs",
                featuresList.size(), epochs);

        for (int epoch = 0; epoch < epochs; epoch++) {
            float totalLoss = 0.0f;

            for (int s = 0; s < featuresList.size(); s++) {
                totalLoss += model.trainSample(featuresList.get(s), labelsList.getFloat(s), learningRate);
            }

            if ((epoch + 1) % 100 == 0) {
                logger.info("Epoch {}/{}, loss: {}",
                        epoch + 1, epochs, totalLoss / featuresList.size());
            }
        }
    }

    private void readTrainingData(Path path,
                                  List<int[]> featuresList,
                                  FloatArrayList labelsList) throws IOException {
        if (!Files.exists(path)) {
            logger.info("Training data file does not exist, skipping: {}", path);
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {
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
                float label;
                if (NsfwDocumentModel.LABEL_NSFW.equals(labelStr)) {
                    label = 1.0f;
                } else if (NsfwDocumentModel.LABEL_SAFE.equals(labelStr)) {
                    label = 0.0f;
                } else {
                    logger.warn("Unknown label '{}', skipping line", labelStr);
                    continue;
                }
                String text = line.substring(spaceIdx + 1);

                featuresList.add(model.extractFeatures(text, ""));
                labelsList.add(label);
            }
        }
    }

    /** List all .txt files in the given directory. */
    static List<Path> listTrainingFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.txt")) {
            for (Path entry : stream) {
                files.add(entry);
            }
        }

        files.sort(Path::compareTo);
        return files;
    }

    /** Split labeled data from the input files into a training file
     *  and a testing file, writing each line to one or the other
     *  based on the given probability.
     *
     * @param trainFraction probability that a line goes to the training set (e.g. 0.8)
     * @param inputPaths labeled data files to read
     * @return a two-element array: [trainingFile, testingFile]
     */
    static Path[] splitTrainingData(double trainFraction, Path... inputPaths) throws IOException {
        Path trainingFile = Files.createTempFile("nsfw-train-", ".txt");
        Path testingFile = Files.createTempFile("nsfw-test-", ".txt");

        Random random = new Random();

        try (BufferedWriter trainWriter = Files.newBufferedWriter(trainingFile);
             BufferedWriter testWriter = Files.newBufferedWriter(testingFile)) {

            for (Path inputPath : inputPaths) {
                if (!Files.exists(inputPath)) {
                    continue;
                }

                try (BufferedReader reader = Files.newBufferedReader(inputPath)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            continue;
                        }

                        if (!line.startsWith("__label__")) {
                            continue;
                        }

                        BufferedWriter writer = random.nextDouble() < trainFraction
                                ? trainWriter : testWriter;
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }
        }

        return new Path[] { trainingFile, testingFile };
    }

    /** Save the trained model to the given directory. */
    public void saveWeights(Path directory) throws IOException {
        model.saveWeights(directory);
    }

    /** Get the trained model for evaluation purposes. */
    NsfwDocumentModel getModel() {
        return model;
    }
}

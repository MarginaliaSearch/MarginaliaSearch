package nu.marginalia.classifier;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BinaryClassifierTrainer {

    private final ClassifierVocabulary vocabulary;
    private final String[] labels;
    private List<ClassifierSample> samples = new ArrayList<>();
    private List<String> samplesRaw = new ArrayList<>();

    public BinaryClassifierTrainer(ClassifierVocabulary vocabulary,
                                   String[] labels,
                                   Path trainingDataDir
                                   ) throws IOException {
        this.vocabulary = vocabulary;
        this.labels = labels;

        for (Path p: Files.newDirectoryStream(trainingDataDir, "*.txt")) {
            readTrainingData(p);
        }
    }

    private void readTrainingData(Path path) throws IOException {
        for (String line: Files.readAllLines(path)) {
            String[] parts = StringUtils.split(line, " ", 2);
            String label = parts[0];
            String sample = parts[1];

            if (labels[0].equals(label)) {
                samples.add(vocabulary.createSample(sample, false));
                samplesRaw.add(sample);
            } else if (labels[1].equals(label)) {
                samples.add(vocabulary.createSample(sample, true));
                samplesRaw.add(sample);
            }
        }
    }


    public void train(Path output) throws IOException {
        Random r = new Random();
        List<ClassifierSample> verificationSamples = new ArrayList<>();
        List<String> verificationSamplesRaw = new ArrayList<>();
        List<ClassifierSample> trainingSamples = new ArrayList<>();

        for (int i = 0; i < samples.size(); i++) {
            ClassifierSample sample = samples.get(i);
            if (r.nextDouble() < 0.9) {
                trainingSamples.add(sample);
            }
            else {
                verificationSamples.add(sample);
                verificationSamplesRaw.add(samplesRaw.get(i));
            }
        }

        BinaryClassifierModel model = BinaryClassifierModel.forTraining(
                vocabulary.size(), 24
        );

        model.train(trainingSamples, 1200, 0.01);

        int truePositives = 0;
        int falsePositives = 0;
        int trueNegatives = 0;
        int falseNegatives = 0;
        int total = 0;
        int correct = 0;

        try (
                PrintWriter falseNegativesWriter = new PrintWriter(
                        Files.newBufferedWriter(Path.of("/tmp/classifier-false-negatives.txt"),
                            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
                        ));
                PrintWriter falsePositivesWriter = new PrintWriter(
                        Files.newBufferedWriter(Path.of("/tmp/classifier-false-positives.txt"),
                                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
                        ))
        )
        {
            for (int si = 0; si < verificationSamples.size(); si++) {
                var sample = verificationSamples.get(si);

                total++;

                if (sample.y0() > 0.5) {
                    if (model.predict(sample.x()) > 0.5) {
                        truePositives++;
                        correct++;
                    } else {
                        falsePositives++;
                        falsePositivesWriter.println(verificationSamplesRaw.get(si));
                    }
                } else {
                    if (model.predict(sample.x()) > 0.5) {
                        falseNegatives++;
                        falseNegativesWriter.println(verificationSamplesRaw.get(si));
                    } else {
                        correct++;
                        trueNegatives++;
                    }
                }
            }
        }



        System.out.println("Total:      " + total);
        System.out.println("Correct:    " + correct);
        System.out.printf("Accuracy:   %.2f%%%n", 100.0 * correct / total);


        System.out.println("True positives:  " + truePositives);
        System.out.println("False positives: " + falsePositives);
        System.out.println("True negatives:  " + trueNegatives);
        System.out.println("False negatives: " + falseNegatives);

        System.out.println();

        double precision = 0.;
        double recall = 0.;
        double f1 = 0.;

        if (truePositives + falsePositives != 0)
            precision = (double) truePositives / (truePositives + falsePositives);
        if (truePositives + falseNegatives != 0)
            recall = (double) truePositives / (truePositives + falseNegatives);
        if (precision + recall != 0)
            f1 = 2 * precision * recall / (precision + recall);

        System.out.printf("Precision:  %.4f%n", precision);
        System.out.printf("Recall:     %.4f%n", recall);
        System.out.printf("F1:         %.4f%n", f1);
        System.out.println();

        model.save(output);
        vocabulary.save(output.resolve("vocabulary.txt"));

    }
}

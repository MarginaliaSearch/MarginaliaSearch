package nu.marginalia.classifier;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class BinaryClassifierTrainer {

    private static boolean SUGGEST_NEW_TERMS = false;

    private final ClassifierVocabulary vocabulary;
    private final BinaryClassifierModel.InputActivationMode inputActivationMode;
    private final String[] labels;
    private List<ClassifierSample> samples = new ArrayList<>();
    private List<String> samplesRaw = new ArrayList<>();

    public BinaryClassifierTrainer(ClassifierVocabulary vocabulary,
                                   BinaryClassifierModel.InputActivationMode inputActivationMode,
                                   String[] labels,
                                   Path trainingDataDir
                                   ) throws IOException {
        this.vocabulary = vocabulary;
        this.inputActivationMode = inputActivationMode;
        this.labels = labels;

        readTrainingData(trainingDataDir);
    }

    private void readTrainingData(Path trainingDataDir) throws IOException {
        Int2IntOpenHashMap positives = new Int2IntOpenHashMap();
        Int2IntOpenHashMap negatives = new Int2IntOpenHashMap();

        Int2ObjectOpenHashMap<List<String>> featuresSamples = new Int2ObjectOpenHashMap<>();

        Map<Integer, Map<String, Integer>> termPairFrequenciesPos = new HashMap<>();
        Map<Integer, Map<String, Integer>> termPairFrequenciesNeg = new HashMap<>();
        Map<String, Integer> termFrequencies = new HashMap<>();

        for (Path path: Files.newDirectoryStream(trainingDataDir)) {
            for (String line : lines(path)) {
                String[] parts = StringUtils.split(line, " ", 2);

                if (parts.length != 2) {
                    System.out.println("Weird line: '" + line + "' in file " + path);
                    continue;
                }

                String label = parts[0];
                String input = parts[1];

                final Int2IntOpenHashMap collection;
                final boolean sampleLabel;

                if (labels[0].equals(label)) {
                    sampleLabel = false;
                    collection = negatives;
                } else if (labels[1].equals(label)) {
                    sampleLabel = true;
                    collection = positives;
                } else {
                    continue;
                }

                var sample = vocabulary.createSample(inputActivationMode, input, sampleLabel);

                if (sample.isEmpty()) {
                    continue;
                }

                Set<String> allTerms = new HashSet<>();
                for (String term : StringUtils.split(input.toLowerCase())) {
                    term = ClassifierVocabulary.trimTerm(term);
                    allTerms.add(term);
                }

                int hash = sample.hashCode();

                var identifiedFeatures = vocabulary.featuresReverse(sample.x());
                for (String term2 : allTerms) {
                    if (identifiedFeatures.contains(term2))
                        continue;

                    {
                        Map<Integer, Map<String, Integer>> set = sample.y0() > 0.5 ? termPairFrequenciesPos : termPairFrequenciesNeg;
                        set.computeIfAbsent(hash, _ -> new HashMap<>()).merge(term2, 1, Integer::sum);
                    }

                    termFrequencies.merge(term2, 1, Integer::sum);
                }


                collection.addTo(hash, 1);

                if (!featuresSamples.containsKey(hash)) {
                    featuresSamples.put(hash, identifiedFeatures);
                }

                samples.add(sample);
                samplesRaw.add(input);
            }
        }

        BitSet toRemove = new BitSet(samples.size());

        // Prune negative labels from ambiguous cases
        for (var entry: featuresSamples.int2ObjectEntrySet()) {
            int posCnt = positives.getOrDefault(entry.getIntKey(), 0);
            int negCnt = negatives.getOrDefault(entry.getIntKey(), 0);

            if (posCnt > 5 && negCnt > 5) {
                System.out.printf("Trimming ambiguous case (%d vs %d): %s\n", posCnt, negCnt, Strings.join(entry.getValue(), ','));

                for (int i = 0; i < samples.size(); i++) {
                    var sample = samples.get(i);

                    if (sample.y0() < 0.5 && sample.hashCode() == entry.getIntKey()) {
                        toRemove.set(i);
                    }
                }

                int featureHash = entry.getIntKey();

                int tcTermPos = termPairFrequenciesPos.get(featureHash).values().stream().mapToInt(Integer::intValue).sum();
                int tcTermNeg = termPairFrequenciesNeg.get(featureHash).values().stream().mapToInt(Integer::intValue).sum();


                var posPairings = termPairFrequenciesPos.getOrDefault(featureHash, Map.of());
                var negPairings = termPairFrequenciesNeg.getOrDefault(featureHash, Map.of());

                Set<String> termsCombined = new HashSet<>(posPairings.size() + negPairings.size());

                termsCombined.addAll(posPairings.keySet());
                termsCombined.addAll(negPairings.keySet());

                Map<String, Double> termsScored = new HashMap<>();
                for (String candidateTerm: termsCombined) {
                    int countPos = posPairings.getOrDefault(candidateTerm, 0);
                    int countNeg = negPairings.getOrDefault(candidateTerm, 0);
                    int tf = termFrequencies.getOrDefault(candidateTerm, posCnt + negCnt);

                    if (countPos + countNeg < 5) continue;
                    if (tf > samples.size() * 0.1) continue;


                    double freqPos = countPos / (double) tcTermPos;
                    double freqNeg = countNeg / (double) tcTermNeg;

                    // Score by chi^2 of frequencies
                    termsScored.put(candidateTerm, (double) (Math.pow(freqNeg - freqPos, 2) / (freqNeg + freqPos)));

                }

                // Suggest new terms that would disambiguate ambiguous terms
                System.out.println("Maybe add:" + termsScored.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue()).limit(15).map(Map.Entry::getKey).collect(Collectors.joining(", ")));
            }
        }

        // Prune samples and raw sample data
        {
            var samplesPruned = new ArrayList<>(samples);
            var samplesRawPruned = new ArrayList<>(samplesRaw);

            for (int i = 0; i < samples.size(); i++) {
                if (!toRemove.get(i)) {
                    samplesPruned.add(samples.get(i));
                    samplesRawPruned.add(samplesRaw.get(i));
                }
            }

            samples.clear();
            samplesRaw.clear();
            samples.addAll(samplesPruned);
            samplesRaw.addAll(samplesRawPruned);
        }
    }

    public static List<String> lines(Path file) throws IOException {
        if (!Files.isRegularFile(file))
            return List.of();

        String fileName = file.toFile().getName();
        if (fileName.endsWith(".txt")) {
            return Files.readAllLines(file);
        }
        else if (fileName.endsWith(".gz")) {
            try (InputStreamReader ir = new InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(file, StandardOpenOption.READ))
            )) {
                return ir.readAllLines();
            }
        }

        // Don't know what to do now
        return List.of();
    }


    public BinaryClassifierModel train() throws IOException {
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
                vocabulary.size(), 24,
                BinaryClassifierModel.InputActivationMode.BINARY
        );

        model.train(trainingSamples, 2000, 0.01);

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
                    if (model.predict(sample) > 0.5) {
                        truePositives++;
                        correct++;
                    } else {
                        falseNegatives++;
                        falseNegativesWriter.println(verificationSamplesRaw.get(si));
                    }
                } else {
                    if (model.predict(sample) > 0.5) {
                        falsePositives++;
                        falsePositivesWriter.println(verificationSamplesRaw.get(si));
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

        return model;
    }
}

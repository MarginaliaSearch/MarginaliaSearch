package nu.marginalia.classifier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class BinaryClassifierModelTest {

    @Test
    public void testSaveLoad(@TempDir Path tempDir) throws IOException {
        var model = BinaryClassifierModel.forTraining(32, 8, BinaryClassifierModel.InputActivationMode.BINARY);
        model.save(tempDir);

        Files.list(tempDir).forEach(System.out::println);

        var model2 = BinaryClassifierModel.fromSerialized(tempDir);
    }

    @Test
    public void testTrain() {
        ClassifierVocabulary vocabulary = new ClassifierVocabulary(
                List.of("sex", "pussy", "ass", "academy", "theory", "java")
        );
        List<ClassifierSample> samples = new ArrayList<>();
        samples.add(vocabulary.createSample(BinaryClassifierModel.InputActivationMode.BINARY, "sex ass", true));
        samples.add(vocabulary.createSample(BinaryClassifierModel.InputActivationMode.BINARY, "pussy", true));
        samples.add(vocabulary.createSample(BinaryClassifierModel.InputActivationMode.BINARY, "academy theory", false));
        samples.add(vocabulary.createSample(BinaryClassifierModel.InputActivationMode.BINARY, "java academy", false));

        System.out.println(samples);

        var model = BinaryClassifierModel.forTraining(vocabulary.size(), 16, BinaryClassifierModel.InputActivationMode.BINARY);

        for (int i = 0; i < 1000; i++) {

            double loss = model.trainingEpoch(samples, 0.1);

            if ((i % 100) == 0)
                System.out.println("loss: " + loss);
        }

        Assertions.assertTrue(model.predict(vocabulary.features("pussy sex")) > 0.75);
        Assertions.assertTrue(model.predict(vocabulary.features("theory java")) < 0.25);
    }

}
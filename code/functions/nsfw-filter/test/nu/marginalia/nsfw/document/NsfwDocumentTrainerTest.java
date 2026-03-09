package nu.marginalia.nsfw.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NsfwDocumentTrainerTest {

    private static final List<String> TEST_FEATURES = List.of(
            "xxx", "nude", "naked", "porn", "sex", "erotic",
            "fetish", "webcam", "hentai", "milf", "pornstar", "cumshot",
            "university", "research", "science", "education",
            "government", "programming", "engineering", "tutorial",
            "documentation", "api", "academic", "campus"
    );

    private Path writeFeaturesFile(Path tempDir) throws IOException {
        Path featuresFile = tempDir.resolve("features.txt");
        Files.write(featuresFile, TEST_FEATURES);
        return featuresFile;
    }

    @Test
    void trainAndEvaluate(@TempDir Path tempDir) throws IOException {
        Path featuresFile = writeFeaturesFile(tempDir);
        Path trainingFile = tempDir.resolve("train.txt");
        Files.write(trainingFile, List.of(
                "__label__NSFW xxx nude naked porn",
                "__label__NSFW sex erotic fetish webcam",
                "__label__NSFW hentai milf pornstar cumshot",
                "__label__SAFE university research science education",
                "__label__SAFE government programming engineering tutorial",
                "__label__SAFE documentation api academic campus"
        ));

        NsfwDocumentTrainer trainer = new NsfwDocumentTrainer(featuresFile);
        trainer.train(0.5f, 500, trainingFile);

        NsfwDocumentModel model = trainer.getModel();

        float nsfwScore = model.forward(model.extractFeatures("xxx nude porn", ""));
        float safeScore = model.forward(model.extractFeatures("university research programming", ""));

        assertTrue(nsfwScore > safeScore,
                "NSFW text should score higher than safe text after training");
    }

    @Test
    void trainWithEmptyFileDoesNotThrow(@TempDir Path tempDir) throws IOException {
        Path featuresFile = writeFeaturesFile(tempDir);
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.write(emptyFile, List.of());

        NsfwDocumentTrainer trainer = new NsfwDocumentTrainer(featuresFile);
        assertDoesNotThrow(() -> trainer.train(0.1f, 10, emptyFile));
    }

    @Test
    void unknownLabelsAreSkipped(@TempDir Path tempDir) throws IOException {
        Path featuresFile = writeFeaturesFile(tempDir);
        Path trainingFile = tempDir.resolve("train.txt");
        Files.write(trainingFile, List.of(
                "__label__UNKNOWN some text here",
                "__label__NSFW xxx porn nude",
                "__label__SAFE university research"
        ));

        NsfwDocumentTrainer trainer = new NsfwDocumentTrainer(featuresFile);
        assertDoesNotThrow(() -> trainer.train(0.1f, 10, trainingFile));
    }

    @Test
    void trainWithMultipleFiles(@TempDir Path tempDir) throws IOException {
        Path featuresFile = writeFeaturesFile(tempDir);
        Path file1 = tempDir.resolve("base.txt");
        Files.write(file1, List.of(
                "__label__NSFW xxx nude naked porn",
                "__label__SAFE university research science education"
        ));

        Path file2 = tempDir.resolve("extra.txt");
        Files.write(file2, List.of(
                "__label__NSFW sex erotic fetish webcam",
                "__label__SAFE government programming engineering tutorial"
        ));

        NsfwDocumentTrainer trainer = new NsfwDocumentTrainer(featuresFile);
        trainer.train(0.5f, 500, file1, file2);

        NsfwDocumentModel model = trainer.getModel();

        float nsfwScore = model.forward(model.extractFeatures("xxx nude porn", ""));
        float safeScore = model.forward(model.extractFeatures("university research programming", ""));

        assertTrue(nsfwScore > safeScore,
                "NSFW text should score higher than safe text after multi-file training");
    }

    @Test
    void trainSkipsMissingFiles(@TempDir Path tempDir) throws IOException {
        Path featuresFile = writeFeaturesFile(tempDir);
        Path existing = tempDir.resolve("train.txt");
        Files.write(existing, List.of(
                "__label__NSFW xxx porn nude",
                "__label__SAFE university research"
        ));

        Path missing = tempDir.resolve("nonexistent.txt");

        NsfwDocumentTrainer trainer = new NsfwDocumentTrainer(featuresFile);
        assertDoesNotThrow(() -> trainer.train(0.1f, 10, existing, missing));
    }

    @Test
    void saveAndLoadWeightsRoundTrip(@TempDir Path tempDir) throws IOException {
        Path featuresFile = writeFeaturesFile(tempDir);
        Path trainingFile = tempDir.resolve("train.txt");
        Files.write(trainingFile, List.of(
                "__label__NSFW xxx porn nude naked",
                "__label__SAFE university research science"
        ));

        NsfwDocumentTrainer trainer = new NsfwDocumentTrainer(featuresFile);
        trainer.train(0.1f, 100, trainingFile);

        Path weightsDir = tempDir.resolve("weights");
        trainer.saveWeights(weightsDir);

        assertTrue(Files.exists(weightsDir));

        NsfwDocumentFilter filter = new NsfwDocumentFilter(weightsDir);

        float proba = filter.nsfwProba("xxx porn", "");
        assertTrue(proba >= 0f && proba <= 1f);
    }
}

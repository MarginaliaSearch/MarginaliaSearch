package nu.marginalia.nsfw.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NsfwDocumentFilterTest {

    private static final List<String> TEST_FEATURES = List.of(
            "xxx", "porn", "nude", "university", "research", "science"
    );

    private Path writeFeaturesFile(Path tempDir) throws IOException {
        Path featuresFile = tempDir.resolve("features.txt");
        Files.write(featuresFile, TEST_FEATURES);
        return featuresFile;
    }

    @Test
    void loadAndInfer(@TempDir Path tempDir) throws IOException {
        Path featuresFile = writeFeaturesFile(tempDir);
        Path trainingFile = tempDir.resolve("train.txt");
        Files.write(trainingFile, List.of(
                "__label__NSFW xxx porn nude",
                "__label__SAFE university research science"
        ));

        NsfwDocumentTrainer trainer = new NsfwDocumentTrainer(featuresFile);
        trainer.train(0.1f, 100, trainingFile);

        Path modelDir = tempDir.resolve("model");
        trainer.saveWeights(modelDir);

        NsfwDocumentFilter filter = new NsfwDocumentFilter(modelDir);

        float proba = filter.nsfwProba("some title", "some description");
        assertTrue(proba >= 0f && proba <= 1f,
                "Probability should be between 0 and 1");
    }

    @Test
    void emptyInputDoesNotThrow(@TempDir Path tempDir) throws IOException {
        Path featuresFile = writeFeaturesFile(tempDir);
        Path trainingFile = tempDir.resolve("train.txt");
        Files.write(trainingFile, List.of(
                "__label__NSFW xxx porn nude",
                "__label__SAFE university research science"
        ));

        NsfwDocumentTrainer trainer = new NsfwDocumentTrainer(featuresFile);
        trainer.train(0.1f, 100, trainingFile);

        Path modelDir = tempDir.resolve("model");
        trainer.saveWeights(modelDir);

        NsfwDocumentFilter filter = new NsfwDocumentFilter(modelDir);

        assertDoesNotThrow(() -> filter.nsfwProba("", ""));
        assertDoesNotThrow(() -> filter.nsfwProba("title", ""));
        assertDoesNotThrow(() -> filter.nsfwProba("", "description"));
    }

    @Test
    void missingDirectoryIsNotLoaded(@TempDir Path tempDir) {
        Path missingDir = tempDir.resolve("missing");
        NsfwDocumentFilter filter = new NsfwDocumentFilter(missingDir);
        assertFalse(filter.isLoaded());
        assertEquals(0.f, filter.nsfwProba("anything", ""));
    }
}

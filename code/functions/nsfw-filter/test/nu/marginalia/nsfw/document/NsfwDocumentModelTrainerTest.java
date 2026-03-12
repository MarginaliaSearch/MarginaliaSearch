package nu.marginalia.nsfw.document;

import nu.marginalia.classifier.BinaryClassifierModel;
import nu.marginalia.classifier.BinaryClassifierTrainer;
import nu.marginalia.classifier.ClassifierVocabulary;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class NsfwDocumentModelTrainerTest {

    @Test
    void trainModel(@TempDir Path tempDir) throws IOException {

        NsfwDocumentModelTrainer.main(tempDir.toString());

        Files.list(tempDir).forEach(System.out::println);

        BinaryClassifierModel.fromSerialized(tempDir);
    }
}
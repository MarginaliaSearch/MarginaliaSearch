package nu.marginalia.nsfw.document;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NsfwDocumentModelTest {

    private static final List<String> TEST_TERMS = List.of("a", "b", "c", "d");

    /** Feature terms used by the extraction tests; includes unigrams,
     *  a bigram, and terms that appear as substrings of common words. */
    private static final List<String> EXTRACTION_TERMS = List.of(
            "xxx", "porn", "nude", "ass", "facial",
            "facial_expression"
    );

    @TempDir
    static Path sharedTempDir;

    private static Path extractionFeaturesFile;

    @BeforeAll
    static void setupFeaturesFile() throws IOException {
        extractionFeaturesFile = sharedTempDir.resolve("features.txt");
        Files.write(extractionFeaturesFile, EXTRACTION_TERMS);
    }

    private NsfwDocumentModel createModel() {
        return new NsfwDocumentModel(TEST_TERMS);
    }

    private NsfwDocumentModel createExtractionModel() throws IOException {
        return NsfwDocumentModel.createForTraining(extractionFeaturesFile);
    }

    @Test
    void loadMismatchedFeatureCountThrows(@TempDir Path tempDir) throws IOException {
        Path weightsDir = tempDir.resolve("weights");

        NsfwDocumentModel model = createModel();
        model.saveWeights(weightsDir);

        // Tamper with features.txt to have a different number of terms
        java.nio.file.Files.write(
                weightsDir.resolve(NsfwDocumentModel.FEATURES_FILENAME),
                List.of("x", "y", "z", "w", "v", "u", "t", "s", "r", "q"));

        assertThrows(Exception.class, () -> new NsfwDocumentModel(weightsDir));
    }

    @Test
    void loadMissingDirectoryThrows(@TempDir Path tempDir) {
        Path missingDir = tempDir.resolve("missing");
        assertThrows(Exception.class, () -> new NsfwDocumentModel(missingDir));
    }

    @Test
    void modelHasFeatures() throws IOException {
        NsfwDocumentModel model = createExtractionModel();
        assertTrue(model.numFeatures() > 0);
    }

    @Test
    void knownUnigramIsDetected() throws IOException {
        NsfwDocumentModel model = createExtractionModel();
        int[] features = model.extractFeatures("xxx content", "");
        assertTrue(features.length >= 1, "Expected at least one feature to fire for 'xxx'");
    }

    @Test
    void unknownTextProducesEmptyArray() throws IOException {
        NsfwDocumentModel model = createExtractionModel();
        int[] features = model.extractFeatures("qwxyz", "zplmk");
        assertEquals(0, features.length, "Expected no features to fire for gibberish text");
    }

    @Test
    void wholeWordMatchOnly() throws IOException {
        NsfwDocumentModel model = createExtractionModel();
        // "ass" should not match inside "assassin"
        int[] featuresAssassin = model.extractFeatures("assassin", "");
        int[] featuresAss = model.extractFeatures("ass", "");

        assertTrue(featuresAss.length > featuresAssassin.length,
                "The word 'ass' should fire more features than 'assassin'");
    }

    @Test
    void bigramFeatureIsDetected() throws IOException {
        NsfwDocumentModel model = createExtractionModel();
        // "facial expression" should fire the facial_expression bigram feature
        int[] featuresBigram = model.extractFeatures("facial expression", "");
        int[] featuresUnigram = model.extractFeatures("facial", "");

        assertTrue(featuresBigram.length > featuresUnigram.length,
                "Bigram 'facial expression' should activate more features than 'facial' alone");
    }

    @Test
    void caseInsensitive() throws IOException {
        NsfwDocumentModel model = createExtractionModel();
        int[] featuresLower = model.extractFeatures("xxx", "");
        int[] featuresUpper = model.extractFeatures("XXX", "");

        assertArrayEquals(featuresLower, featuresUpper,
                "Feature extraction should be case-insensitive");
    }

    @Test
    void titleAndDescriptionAreBothUsed() throws IOException {
        NsfwDocumentModel model = createExtractionModel();
        int[] featuresInTitle = model.extractFeatures("xxx", "");
        int[] featuresInDesc = model.extractFeatures("", "xxx");

        assertArrayEquals(featuresInTitle, featuresInDesc,
                "Features should be extracted from both title and description");
    }

    @Test
    void featureVectorIndicesAreValid() throws IOException {
        NsfwDocumentModel model = createExtractionModel();
        int[] features = model.extractFeatures("xxx porn nude", "");
        for (int idx : features) {
            assertTrue(idx >= 0 && idx < model.numFeatures(),
                    "Feature index " + idx + " out of bounds");
        }
    }
}

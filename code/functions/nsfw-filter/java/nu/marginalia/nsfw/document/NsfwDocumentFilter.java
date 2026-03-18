package nu.marginalia.nsfw.document;

import com.google.inject.Inject;
import nu.marginalia.WmsaHome;
import nu.marginalia.classifier.BinaryClassifierModel;
import nu.marginalia.classifier.ClassifierSample;
import nu.marginalia.classifier.ClassifierVocabulary;
import nu.marginalia.language.model.DocumentSentence;
import org.checkerframework.checker.units.qual.C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static nu.marginalia.classifier.BinaryClassifierModel.InputActivationMode.BINARY;
import static nu.marginalia.classifier.BinaryClassifierModel.InputActivationMode.COUNTED;

public class NsfwDocumentFilter {
    private final boolean isLoaded;
    private final BinaryClassifierModel model;
    private final ClassifierVocabulary vocabulary;

    private final double activationThreshold = 0.75;

    private final Logger logger = LoggerFactory.getLogger(NsfwDocumentFilter.class);

    @Inject
    public NsfwDocumentFilter() {
        Path modelPath = WmsaHome.getModelPath().resolve("nsfw-model");

        boolean isLoaded = false;
        ClassifierVocabulary voacbulary = null;
        BinaryClassifierModel model = null;

        try {
            voacbulary = new ClassifierVocabulary(modelPath.resolve("vocabulary.txt"));
            model = BinaryClassifierModel.fromSerialized(modelPath);
            isLoaded = true;
        }
        catch (IOException ex) {
            logger.info("Failed to load NSFW model", ex);
        }
        finally {
            this.model = model;
            this.vocabulary = voacbulary;
            this.isLoaded = isLoaded;
        }
    }

    public boolean isNsfw(String title, String description) {
        if (!isLoaded)
            return false;

        if (model.inputActivationMode == BINARY) {
            int features[] = vocabulary.features(title, description);

            if (features.length == 0)
                return false;

            return model.predict(features) > activationThreshold;
        }
        else if (model.inputActivationMode == COUNTED) {

            Map.Entry<int[], int[]> countedFeatures = vocabulary.countedFeatures(title, description);

            int features[] = countedFeatures.getKey();

            if (features.length == 0)
                return false;

            return model.predict(features, ClassifierSample.activationFromCount(countedFeatures.getValue())) > activationThreshold;
        }
        else throw new IllegalStateException("Unknown enum value " + model.inputActivationMode);
    }

    public boolean isNsfw(List<DocumentSentence> sentences) {
        if (!isLoaded)
            return false;

        // Model is not appropriate for longer texts
        if (model.inputActivationMode == BINARY)
            return false;

        Map.Entry<int[], int[]> featuresAndCounts
                = vocabulary.countedFeatures(sentences);

        int[] features = featuresAndCounts.getKey();

        if (features.length == 0)
            return false;


        return model.predict(features, ClassifierSample.activationFromCount(featuresAndCounts.getValue())) > activationThreshold;
    }


}

package nu.marginalia.nsfw.document;

import com.google.inject.Inject;
import nu.marginalia.WmsaHome;
import nu.marginalia.classifier.BinaryClassifierModel;
import nu.marginalia.classifier.ClassifierVocabulary;
import nu.marginalia.language.model.DocumentSentence;
import org.checkerframework.checker.units.qual.C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class NsfwDocumentFilter {
    private final boolean isLoaded;
    private final BinaryClassifierModel model;
    private final ClassifierVocabulary vocabulary;

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

        int[] features = vocabulary.features(title, description);

        if (features.length == 0)
            return false;

        return model.predict(features) > 0.75;
    }

    public boolean isNsfw(List<DocumentSentence> sentences) {
        if (!isLoaded)
            return false;

        int[] features = vocabulary.features(sentences);

        if (features.length == 0)
            return false;

        return model.predict(features) > 0.75;
    }
}

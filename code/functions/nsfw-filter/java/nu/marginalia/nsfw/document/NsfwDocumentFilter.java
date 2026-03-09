package nu.marginalia.nsfw.document;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.WmsaHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/** NSFW document classifier for inference.  Uses a pre-trained
 *  neural network model to estimate the probability that a
 *  document is NSFW based on its title and description.
 */
@Singleton
public class NsfwDocumentFilter {

    private static final Logger logger = LoggerFactory.getLogger(NsfwDocumentFilter.class);

    private final NsfwDocumentModel model;
    private final boolean modelLoaded;

    @Inject
    public NsfwDocumentFilter() {
        this(WmsaHome.getModelsPath().resolve("nsfw-model"));
    }

    public NsfwDocumentFilter(Path modelPath) {
        NsfwDocumentModel model = null;
        boolean loadedOk = true;

        try {
            model = new NsfwDocumentModel(modelPath);
        } catch (Exception ex) {
            logger.warn("Failed to load NSFW model", ex);
            loadedOk = false;
        }

        this.model = model;
        this.modelLoaded = loadedOk;
    }

    public boolean isLoaded() {
        return modelLoaded;
    }

    /** Return the probability that the document is NSFW based on
     *  its title and description.
     */
    public float nsfwProba(String title, String description) {
        if (modelLoaded == false)
            return 0.f;

        int[] features = model.extractFeatures(title, description);
        return model.forward(features);
    }
}

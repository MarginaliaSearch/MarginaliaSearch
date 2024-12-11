package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.converting.processor.logic.TitleExtractor;
import nu.marginalia.converting.processor.summary.SummaryExtractor;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PhpBBSpecialization extends DefaultSpecialization {
    private static final Logger logger = LoggerFactory.getLogger(PhpBBSpecialization.class);

    @Inject
    public PhpBBSpecialization(SummaryExtractor summaryExtractor, TitleExtractor titleExtractor) {
        super(summaryExtractor, titleExtractor);
    }

    @Override
    public boolean shouldIndex(EdgeUrl url) {
        return url.path.contains("viewtopic.php");
    }
}

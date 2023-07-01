package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.summary.SummaryExtractor;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Singleton
public class PhpBBSpecialization extends DefaultSpecialization {
    private static final Logger logger = LoggerFactory.getLogger(PhpBBSpecialization.class);

    @Inject
    public PhpBBSpecialization(SummaryExtractor summaryExtractor) {
        super(summaryExtractor);
    }

    @Override
    public boolean shouldIndex(EdgeUrl url) {
        return url.path.contains("viewtopic.php");
    }
}

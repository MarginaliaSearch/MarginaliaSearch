package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.converting.processor.logic.TitleExtractor;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Singleton
public class JavadocSpecialization extends DefaultSpecialization {
    private static final Logger logger = LoggerFactory.getLogger(JavadocSpecialization.class);

    @Inject
    public JavadocSpecialization(TitleExtractor titleExtractor) {
        super(titleExtractor);
    }

    @Override
    public Document prune(Document doc) {
        final var prunedDoc = super.prune(doc);

        prunedDoc.getElementsByTag("noscript").remove();

        return prunedDoc;
    }
}
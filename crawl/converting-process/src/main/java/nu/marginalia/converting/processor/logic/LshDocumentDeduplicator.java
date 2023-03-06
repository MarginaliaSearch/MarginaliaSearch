package nu.marginalia.converting.processor.logic;

import com.google.inject.Singleton;
import nu.marginalia.model.crawl.EdgeUrlState;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.lsh.EasyLSH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Deduplicates documents based on their LSH
 *
 * @see EasyLSH
 */
@Singleton
public class LshDocumentDeduplicator {

    private final int DISTANCE_THRESHOLD = 2;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void deduplicate(List<ProcessedDocument> documents) {
        Set<ProcessedDocument> goodDocuments = documents.stream()
                .filter(ProcessedDocument::isProcessedFully)
                .collect(Collectors.toSet());

        for (var document : documents) {
            if (!goodDocuments.contains(document)) {
                continue;
            }

            goodDocuments.removeIf(other -> removeIfDuplicate(document, other));
        }
    }

    private boolean removeIfDuplicate(ProcessedDocument thisDoc, ProcessedDocument otherDoc) {
        if (thisDoc == otherDoc)
            return false;

        if (thisDoc.words.size() < 100
        || otherDoc.words.size() < 100) {
            return false;
        }

        if (EasyLSH.hammingDistance(thisDoc.details.hashCode, otherDoc.details.hashCode) > DISTANCE_THRESHOLD)
            return false;

        if (thisDoc.url.path.length()
                < otherDoc.url.path.length())
        {
            logger.debug("{} duplicates {}", otherDoc.url, thisDoc.url);

            otherDoc.state = EdgeUrlState.DISQUALIFIED;
            otherDoc.stateReason = "Duplicate";

            return true;
        }

        return false;

    }
}
